package com.fintry.finance;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReconciliationRuleService {
  public record Input(
      @NotNull UUID requestId,
      Long revision,
      @NotNull UUID accountId,
      @NotBlank @Size(max = 120) String name,
      @NotNull FinancialEntry.Type direction,
      @NotBlank @Size(min = 3, max = 80) String bankText,
      @NotBlank @Size(min = 3, max = 80) String entryText,
      @Min(0) @Max(30) int windowDays,
      boolean active) {}

  public record View(
      UUID id,
      long revision,
      UUID accountId,
      String name,
      String author,
      FinancialEntry.Type direction,
      String bankText,
      String entryText,
      int windowDays,
      boolean active) {}

  public record Result(List<View> content, int number, int totalPages, long totalElements) {}

  private final CurrentUser current;
  private final CompanyRepository companies;
  private final AccountRepository accounts;
  private final ReconciliationRuleRepository rules;
  private final AuditService audit;

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Esta regra mudou. Atualize a lista antes de salvar.");
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Regra ou conta não encontrada.");
  }

  public static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void account(UUID id) {
    accounts.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private View view(ReconciliationRule r) {
    return new View(
        r.getId(),
        r.getRevision(),
        r.getAccountId(),
        r.getName(),
        r.getAuthor(),
        r.getDirection(),
        r.getBankText(),
        r.getEntryText(),
        r.getWindowDays(),
        r.isActive());
  }

  public List<ReconciliationRule> active(UUID account) {
    return rules.findByCompanyIdAndAccountIdAndActiveTrueOrderByNameAscIdAsc(
        current.companyId(), account);
  }

  public List<ReconciliationRule> matching(
      StatementLine line, FinancialEntry entry, List<ReconciliationRule> available) {
    long days =
        Math.abs(
            ChronoUnit.DAYS.between(
                line.getPostedDate(),
                entry.getStatus() == FinancialEntry.Status.PAID
                    ? entry.getPaymentDate()
                    : entry.getDueDate()));
    return available.stream()
        .filter(
            r ->
                r.getAccountId().equals(line.getAccountId())
                    && r.getDirection() == entry.getType()
                    && normalize(line.getDescription()).contains(normalize(r.getBankText()))
                    && normalize(entry.getDescription()).contains(normalize(r.getEntryText()))
                    && days <= r.getWindowDays())
        .toList();
  }

  public Result list(UUID account, Boolean active, int page) {
    account(account);
    if (page < 0) throw invalid("Página inválida.");
    Specification<ReconciliationRule> spec =
        (r, q, cb) ->
            cb.and(
                cb.equal(r.get("companyId"), current.companyId()),
                cb.equal(r.get("accountId"), account),
                active == null ? cb.conjunction() : cb.equal(r.get("active"), active));
    var p = rules.findAll(spec, PageRequest.of(page, 20, Sort.by("name", "id")));
    return new Result(
        p.stream().map(this::view).toList(), page, p.getTotalPages(), p.getTotalElements());
  }

  @Transactional
  public View save(UUID id, Input in) {
    companies.lock(current.companyId()).orElseThrow(this::missing);
    account(in.accountId());
    if (normalize(in.bankText()).length() < 3 || normalize(in.entryText()).length() < 3)
      throw invalid("Use pelo menos três letras ou números em cada texto da regra.");
    String fingerprint =
        hash(
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .writeValueAsString(
                    List.of(
                        in.accountId(),
                        in.name().strip(),
                        in.direction(),
                        in.bankText().strip(),
                        in.entryText().strip(),
                        in.windowDays(),
                        in.active())));
    if (id == null) {
      var prior = rules.findByCompanyIdAndCreationKey(current.companyId(), in.requestId());
      if (prior.isPresent()) {
        if (!prior.get().getCreationHash().equals(fingerprint)) throw conflict();
        return view(prior.get());
      }
    }
    var r =
        id == null
            ? new ReconciliationRule()
            : rules.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
    if (id != null && (in.revision() == null || !in.revision().equals(r.getRevision())))
      throw conflict();
    if (in.active()
        && (!r.isActive() || !in.accountId().equals(r.getAccountId()))
        && active(in.accountId()).size() >= 50)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Esta conta já tem 50 regras ativas. Desative uma antes de ativar outra.");
    var before = id == null ? Map.<String, String>of() : snapshot(r);
    if (id == null) {
      r.setCompanyId(current.companyId());
      r.setCreationKey(in.requestId());
      r.setCreationHash(fingerprint);
      r.setAuthor(current.get().getName());
    }
    r.setAccountId(in.accountId());
    r.setName(in.name().strip());
    r.setDirection(in.direction());
    r.setBankText(in.bankText().strip());
    r.setEntryText(in.entryText().strip());
    r.setWindowDays(in.windowDays());
    r.setActive(in.active());
    rules.saveAndFlush(r);
    audit.change(
        id == null ? "RECONCILIATION_RULE_CREATE" : "RECONCILIATION_RULE_UPDATE",
        "RECONCILIATION_RULE",
        r.getId(),
        r.getName(),
        before,
        snapshot(r));
    return view(r);
  }

  private Map<String, String> snapshot(ReconciliationRule r) {
    return AuditService.fields(
        "Nome",
        r.getName(),
        "Conta",
        r.getAccountId(),
        "Direção",
        r.getDirection(),
        "Texto do banco",
        r.getBankText(),
        "Texto do lançamento",
        r.getEntryText(),
        "Janela de dias",
        r.getWindowDays(),
        "Ativa",
        r.isActive());
  }
}
