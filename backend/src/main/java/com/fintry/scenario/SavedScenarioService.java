package com.fintry.scenario;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Definitions and immutable calculation snapshots. No financial mutation is invoked here. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SavedScenarioService {
  public record Save(
      @NotNull UUID requestId,
      @NotBlank @Size(max = 120) String name,
      Long revision,
      @NotNull @Valid ScenarioService.Input input) {}

  public record Calculate(@NotNull UUID requestId, @Min(0) long revision) {}

  public record Archive(@Min(0) long revision, boolean archived) {}

  public record RunSummary(
      UUID id,
      long revision,
      String author,
      Instant calculatedAt,
      String closingBalance,
      String delta) {}

  public record View(
      UUID id,
      String name,
      String author,
      long revision,
      boolean archived,
      Instant updatedAt,
      ScenarioService.Input input,
      RunSummary latest,
      boolean stale) {}

  public record PageView(List<View> content, long totalElements, int totalPages, int number) {}

  public record History(List<RunSummary> content, long totalElements, int totalPages, int number) {}

  public record RunView(
      RunSummary summary, ScenarioService.Input input, ScenarioService.Preview result) {}

  public record Comparison(RunView left, RunView right, String closingChange) {}

  private final CurrentUser current;
  private final CompanyRepository companies;
  private final SavedScenarioRepository saved;
  private final ScenarioRunRepository runs;
  private final ScenarioService calculator;
  private final AuditService audit;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final JsonMapper json = JsonMapper.builder().build();

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cenário ou cálculo não encontrado.");
  }

  private ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Este cenário mudou. Abra a versão atual antes de salvar ou recalcular.");
  }

  private SavedScenario get(UUID id) {
    return saved.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private void lock() {
    companies.lock(current.companyId()).orElseThrow(this::missing);
  }

  private void revision(SavedScenario s, long revision) {
    if (s.getRevision() != revision) throw conflict();
  }

  private String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String encode(Object value) {
    return json.writeValueAsString(value);
  }

  private <T> T decode(String value, Class<T> type) {
    return json.readValue(value, type);
  }

  // Conservative invalidation: any financial record change in the company marks snapshots stale.
  private String fingerprint() {
    UUID c = current.companyId();
    var entries =
        jdbc.queryForMap(
            "select count(*) as total,max(updated_at) as changed,sum(amount) as"
                + " amount,sum(paid_amount) as paid from financial_entries where company_id=?",
            c);
    var accounts =
        jdbc.queryForMap(
            "select count(*) as total,max(updated_at) as changed,sum(initial_balance) as opening"
                + " from financial_accounts where company_id=?",
            c);
    return hash(LocalDate.now(clock) + "|" + entries + "|" + accounts);
  }

  private ScenarioService.Input definition(ScenarioService.Input i) {
    return new ScenarioService.Input(
        i.days(),
        i.accountId(),
        i.includeOverdue(),
        i.shifts() == null ? List.of() : i.shifts(),
        i.hypotheses() == null ? List.of() : i.hypotheses(),
        null);
  }

  private RunSummary summary(ScenarioRun r) {
    var p = decode(r.getResult(), ScenarioService.Preview.class);
    return new RunSummary(
        r.getId(),
        r.getDefinitionRevision(),
        r.getAuthor(),
        r.getCalculatedAt(),
        p.scenario().closingBalance(),
        p.closingDelta());
  }

  private View view(SavedScenario s, String fingerprint) {
    var latest =
        runs.findFirstByScenarioIdAndCompanyIdOrderByCreatedAtDescIdDesc(
                s.getId(), current.companyId())
            .orElse(null);
    return new View(
        s.getId(),
        s.getName(),
        s.getAuthor(),
        s.getRevision(),
        s.isArchived(),
        s.getModifiedAt(),
        decode(s.getDefinition(), ScenarioService.Input.class),
        latest == null ? null : summary(latest),
        latest == null
            || latest.getDefinitionRevision() != s.getRevision()
            || !latest.getFingerprint().equals(fingerprint));
  }

  public PageView list(boolean archived, int page) {
    if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
    var p =
        saved.findByCompanyIdAndArchivedOrderByUpdatedAtDescIdDesc(
            current.companyId(), archived, PageRequest.of(page, 12));
    String f = fingerprint();
    return new PageView(
        p.stream().map(s -> view(s, f)).toList(), p.getTotalElements(), p.getTotalPages(), page);
  }

  public View details(UUID id) {
    return view(get(id), fingerprint());
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public View save(UUID id, Save input) {
    lock();
    var definition = definition(input.input());
    String encoded = encode(definition), name = input.name().strip();
    String creationHash = hash(name + encoded);
    if (id == null) {
      var prior = saved.findByCompanyIdAndCreationKey(current.companyId(), input.requestId());
      if (prior.isPresent()) {
        if (!prior.get().getCreationHash().equals(creationHash)) throw conflict();
        return view(prior.get(), fingerprint());
      }
    }
    var s = id == null ? new SavedScenario() : get(id);
    if (id != null) {
      var previous =
          runs.findByScenarioIdAndCompanyIdAndRequestId(id, current.companyId(), input.requestId());
      if (previous.isPresent()) {
        if (previous.get().getDefinitionRevision() == s.getRevision()
            && previous.get().getDefinition().equals(encoded)
            && s.getName().equals(name)) return view(s, fingerprint());
        throw conflict();
      }
      if (input.revision() == null) throw conflict();
      revision(s, input.revision());
      if (s.isArchived())
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Restaure o cenário antes de editar.");
    }
    var result =
        calculator.preview(definition); // Validate references and scope before persisting anything.
    if (id == null) {
      s.setCompanyId(current.companyId());
      s.setCreatedBy(current.get().getId());
      s.setAuthor(current.get().getName());
      s.setCreationKey(input.requestId());
      s.setCreationHash(creationHash);
    }
    s.setName(name);
    s.setDefinition(encoded);
    s.setModifiedAt(Instant.now(clock));
    saved.saveAndFlush(s);
    record(s, input.requestId(), result);
    audit.eventAs(
        current.get(),
        id == null ? "SCENARIO_CREATE" : "SCENARIO_UPDATE",
        "SCENARIO",
        s.getId(),
        name);
    return view(s, fingerprint());
  }

  private ScenarioRun record(SavedScenario s, UUID request, ScenarioService.Preview result) {
    var prior =
        runs.findByScenarioIdAndCompanyIdAndRequestId(s.getId(), current.companyId(), request);
    if (prior.isPresent()) {
      if (prior.get().getDefinitionRevision() != s.getRevision()) throw conflict();
      return prior.get();
    }
    var r = new ScenarioRun();
    r.setCalculatedAt(Instant.now(clock));
    r.setCompanyId(current.companyId());
    r.setScenarioId(s.getId());
    r.setRequestId(request);
    r.setDefinitionRevision(s.getRevision());
    r.setAuthor(current.get().getName());
    r.setDefinition(s.getDefinition());
    r.setResult(encode(result));
    r.setFingerprint(fingerprint());
    return runs.saveAndFlush(r);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public RunView calculate(UUID id, Calculate input) {
    lock();
    var s = get(id);
    revision(s, input.revision());
    if (s.isArchived())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Restaure o cenário antes de recalcular.");
    var prior =
        runs.findByScenarioIdAndCompanyIdAndRequestId(id, current.companyId(), input.requestId());
    if (prior.isPresent()) return runView(prior.get());
    var r =
        record(
            s,
            input.requestId(),
            calculator.preview(decode(s.getDefinition(), ScenarioService.Input.class)));
    audit.eventAs(current.get(), "SCENARIO_CALCULATE", "SCENARIO", id, s.getName());
    return runView(r);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public View archive(UUID id, Archive input) {
    lock();
    var s = get(id);
    revision(s, input.revision());
    s.setArchived(input.archived());
    s.setModifiedAt(Instant.now(clock));
    saved.saveAndFlush(s);
    audit.eventAs(
        current.get(),
        input.archived() ? "SCENARIO_ARCHIVE" : "SCENARIO_RESTORE",
        "SCENARIO",
        id,
        s.getName());
    return view(s, fingerprint());
  }

  public History history(UUID id, int page) {
    get(id);
    if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
    var p =
        runs.findByScenarioIdAndCompanyIdOrderByCreatedAtDescIdDesc(
            id, current.companyId(), PageRequest.of(page, 20));
    return new History(
        p.stream().map(this::summary).toList(), p.getTotalElements(), p.getTotalPages(), page);
  }

  private RunView runView(ScenarioRun r) {
    return new RunView(
        summary(r),
        decode(r.getDefinition(), ScenarioService.Input.class),
        decode(r.getResult(), ScenarioService.Preview.class));
  }

  public RunView run(UUID id, UUID run) {
    get(id);
    return runView(
        runs.findByIdAndScenarioIdAndCompanyId(run, id, current.companyId())
            .orElseThrow(this::missing));
  }

  public Comparison compare(UUID id, UUID left, UUID right) {
    var a = run(id, left);
    var b = run(id, right);
    return new Comparison(
        a,
        b,
        new BigDecimal(b.result().scenario().closingBalance())
            .subtract(new BigDecimal(a.result().scenario().closingBalance()))
            .setScale(2)
            .toPlainString());
  }
}
