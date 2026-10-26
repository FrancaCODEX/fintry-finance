package com.fintry.finance;

import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesService {
  private final CurrentUser current;
  private final com.fintry.audit.AuditService audit;
  private final CompanyRepository companies;
  private final SeriesRepository series;
  private final EntryRepository entries;
  private final FinanceService finance;

  public record Input(
      @NotNull UUID requestId,
      @NotNull FinancialSeries.Kind kind,
      @Min(2) @Max(120) int occurrences,
      @NotNull @Valid FinanceService.EntryInput entry) {}

  public record View(
      UUID id,
      String description,
      FinancialSeries.Kind kind,
      FinancialEntry.Type type,
      int occurrences,
      BigDecimal totalAmount,
      LocalDate firstDueDate,
      LocalDate lastDueDate,
      int paid,
      int partial,
      int pending,
      int pausedOccurrences,
      int cancelled,
      boolean paused,
      boolean stopped) {}

  public record PageView(List<View> content, long totalElements, int totalPages, int number) {}

  public record Created(View series, boolean created) {}

  public record Item(int sequence, LocalDate dueDate, BigDecimal amount) {}

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada.");
  }

  /**
   * Always derive each date from the original anchor; February must not move a March 31 due date.
   */
  public static List<Item> schedule(
      FinancialSeries.Kind kind, int count, BigDecimal amount, LocalDate first) {
    if (count < 2
        || count > 120
        || kind == null
        || amount == null
        || first == null
        || amount.signum() <= 0)
      throw new IllegalArgumentException("Configuração de série inválida.");
    BigInteger cents = amount.movePointRight(2).toBigIntegerExact();
    BigInteger[] division = cents.divideAndRemainder(BigInteger.valueOf(count));
    if (kind == FinancialSeries.Kind.INSTALLMENT && division[0].signum() == 0)
      throw new IllegalArgumentException("O total deve permitir pelo menos R$ 0,01 por parcela.");
    var result = new ArrayList<Item>();
    for (int i = 0; i < count; i++) {
      BigInteger value =
          kind == FinancialSeries.Kind.RECURRING
              ? cents
              : division[0].add(i < division[1].intValueExact() ? BigInteger.ONE : BigInteger.ZERO);
      result.add(new Item(i + 1, first.plusMonths(i), new BigDecimal(value, 2)));
    }
    return result;
  }

  private String fingerprint(Input input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var e = input.entry();
      // Length-prefixed fields avoid ambiguous separators in user-provided text.
      for (Object value :
          Arrays.asList(
              input.kind(),
              input.occurrences(),
              e.accountId(),
              e.description(),
              e.category(),
              e.type(),
              e.amount().stripTrailingZeros(),
              e.dueDate(),
              e.paid(),
              e.paid() ? e.paymentDate() : null,
              e.categoryId(),
              e.contactId())) {
        byte[] bytes = (value == null ? "" : value.toString()).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Transactional
  public Created create(Input input) {
    var company = current.companyId();
    companies.lock(company).orElseThrow(this::missing);
    String hash = fingerprint(input);
    var previous = series.findByCompanyIdAndRequestId(company, input.requestId());
    if (previous.isPresent()) {
      if (!previous.get().getFingerprint().equals(hash))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Esta solicitação já foi usada com outros dados. Abra um novo formulário.");
      return new Created(
          view(
              previous.get(),
              entries.findByCompanyIdAndSeriesIdIn(company, List.of(previous.get().getId()))),
          false);
    }
    List<Item> plan;
    try {
      plan =
          schedule(
              input.kind(), input.occurrences(), input.entry().amount(), input.entry().dueDate());
    } catch (IllegalArgumentException | ArithmeticException | DateTimeException ex) {
      throw invalid(
          "Confira a quantidade, o valor e os vencimentos da série. Cada parcela deve ter ao menos"
              + " R$ 0,01.");
    }
    var template = input.entry();
    var s = new FinancialSeries();
    s.setCompanyId(company);
    s.setRequestId(input.requestId());
    s.setFingerprint(hash);
    s.setDescription(template.description().strip());
    s.setKind(input.kind());
    s.setType(template.type());
    s.setOccurrences(input.occurrences());
    s.setTotalAmount(plan.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    s.setFirstDueDate(plan.get(0).dueDate());
    s.setLastDueDate(plan.get(plan.size() - 1).dueDate());
    series.saveAndFlush(s);
    for (var item : plan) {
      var generated =
          new FinanceService.EntryInput(
              template.accountId(),
              template.description(),
              template.category(),
              template.type(),
              item.amount(),
              item.dueDate(),
              item.sequence() == 1 && template.paid(),
              item.sequence() == 1 ? template.paymentDate() : null,
              template.categoryId(),
              template.contactId());
      var created = finance.createEntry(generated);
      var entry = entries.findById(created.id()).orElseThrow();
      entry.setSeriesId(s.getId());
      entry.setSequenceNumber(item.sequence());
      entry.setSeriesCount(plan.size());
      entry.setSeriesKind(s.getKind());
    }
    audit.change(
        "SERIES_CREATE",
        "SERIES",
        s.getId(),
        s.getDescription(),
        Map.of(),
        com.fintry.audit.AuditService.fields(
            "Tipo",
            s.getKind(),
            "Ocorrências",
            s.getOccurrences(),
            "Valor total",
            s.getTotalAmount(),
            "Primeiro vencimento",
            s.getFirstDueDate(),
            "Último vencimento",
            s.getLastDueDate()));
    return new Created(
        view(s, entries.findByCompanyIdAndSeriesIdIn(company, List.of(s.getId()))), true);
  }

  private View view(FinancialSeries s, List<FinancialEntry> items) {
    int paid = 0, partial = 0, pending = 0, paused = 0, cancelled = 0;
    for (var e : items) {
      if (e.isSeriesPaused()) {
        paused++;
        continue;
      }
      switch (e.getStatus()) {
        case PAID -> paid++;
        case PARTIAL -> partial++;
        case PENDING -> pending++;
        case CANCELLED -> cancelled++;
      }
    }
    return new View(
        s.getId(),
        s.getDescription(),
        s.getKind(),
        s.getType(),
        s.getOccurrences(),
        s.getTotalAmount(),
        s.getFirstDueDate(),
        s.getLastDueDate(),
        paid,
        partial,
        pending,
        paused,
        cancelled,
        s.isPaused(),
        s.isStopped());
  }

  public PageView list(int page, int size) {
    if (page < 0 || size < 1 || size > 50) throw invalid("Paginação inválida.");
    var company = current.companyId();
    var result =
        series.findByCompanyId(
            company,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    var ids = result.getContent().stream().map(FinancialSeries::getId).toList();
    Map<UUID, List<FinancialEntry>> grouped = new HashMap<>();
    if (!ids.isEmpty())
      for (var e : entries.findByCompanyIdAndSeriesIdIn(company, ids))
        grouped.computeIfAbsent(e.getSeriesId(), k -> new ArrayList<>()).add(e);
    return new PageView(
        result.stream().map(s -> view(s, grouped.getOrDefault(s.getId(), List.of()))).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page);
  }

  @Transactional
  public View stop(UUID id) {
    var company = current.companyId();
    var s = series.lock(id, company).orElseThrow(this::missing);
    // Same entry locks as payment/cancellation: never revert a payment that won the race.
    var items = entries.lockSeries(id, company);
    if (s.isStopped()) return view(s, items);
    var before = com.fintry.audit.AuditService.fields("Encerrada", s.isStopped());
    for (var e : items)
      if (e.getStatus() == FinancialEntry.Status.PENDING) {
        finance.requireUnreconciled(e.getId());
        var old = finance.snapshot(e);
        e.setStatus(FinancialEntry.Status.CANCELLED);
        e.setSeriesPaused(false);
        finance.recordChange("CANCEL", e, old);
      } else if (e.isSeriesPaused()) {
        e.setSeriesPaused(false);
      }
    s.setPaused(false);
    s.setStopped(true);
    audit.change(
        "SERIES_STOP",
        "SERIES",
        s.getId(),
        s.getDescription(),
        before,
        com.fintry.audit.AuditService.fields("Encerrada", true));
    return view(s, items);
  }

  @Transactional
  public View pause(UUID id) {
    var company = current.companyId();
    var s = series.lock(id, company).orElseThrow(this::missing);
    var items = entries.lockSeries(id, company);
    if (s.getKind() != FinancialSeries.Kind.RECURRING)
      throw invalid("Somente recorrências mensais podem ser pausadas.");
    if (s.isStopped()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A recorrência já foi encerrada.");
    if (s.isPaused()) return view(s, items);
    var before = com.fintry.audit.AuditService.fields("Pausada", false);
    var today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    for (var e : items)
      if (e.getStatus() == FinancialEntry.Status.PENDING && e.getDueDate().isAfter(today)) {
        finance.requireUnreconciled(e.getId());
        var old = finance.snapshot(e);
        e.setStatus(FinancialEntry.Status.CANCELLED);
        e.setSeriesPaused(true);
        finance.recordChange("PAUSE_SERIES_OCCURRENCE", e, old);
      }
    s.setPaused(true);
    audit.change(
        "SERIES_PAUSE",
        "SERIES",
        s.getId(),
        s.getDescription(),
        before,
        com.fintry.audit.AuditService.fields("Pausada", true, "Pendências futuras suspensas", items.stream().filter(FinancialEntry::isSeriesPaused).count()));
    return view(s, items);
  }

  @Transactional
  public View resume(UUID id) {
    var company = current.companyId();
    var s = series.lock(id, company).orElseThrow(this::missing);
    var items = entries.lockSeries(id, company);
    if (s.isStopped()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A recorrência encerrada não pode ser retomada.");
    if (!s.isPaused()) return view(s, items);
    var before = com.fintry.audit.AuditService.fields("Pausada", true);
    for (var e : items)
      if (e.isSeriesPaused() && e.getStatus() == FinancialEntry.Status.CANCELLED) {
        var old = finance.snapshot(e);
        e.setStatus(FinancialEntry.Status.PENDING);
        e.setSeriesPaused(false);
        finance.recordChange("RESUME_SERIES_OCCURRENCE", e, old);
      }
    s.setPaused(false);
    audit.change(
        "SERIES_RESUME",
        "SERIES",
        s.getId(),
        s.getDescription(),
        before,
        com.fintry.audit.AuditService.fields("Pausada", false));
    return view(s, items);
  }
}
