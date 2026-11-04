package com.fintry.scenario;

import com.fintry.auth.CurrentUser;
import com.fintry.finance.*;
import com.fintry.report.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only what-if calculations. Hypotheses are never persisted as financial movements. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ScenarioService {
  public record Shift(@NotNull UUID entryId, @Min(1) @Max(366) int delayDays) {}

  public record Hypothesis(
      @NotBlank @Size(max = 120) String label,
      @NotNull FinancialEntry.Type type,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull LocalDate date,
      @Min(1) @Max(12) int occurrences) {}

  public record Input(
      @Min(1) @Max(366) int days,
      UUID accountId,
      boolean includeOverdue,
      @Size(max = 20) List<@NotNull @Valid Shift> shifts,
      @Size(max = 20) List<@NotNull @Valid Hypothesis> hypotheses,
      LocalDate explainDate) {}

  public record Point(LocalDate date, String income, String expense, String balance) {}

  public record Projection(
      LocalDate start,
      LocalDate end,
      String openingBalance,
      String closingBalance,
      String pendingIncome,
      String pendingExpense,
      String minimumBalance,
      LocalDate firstNegativeDate,
      List<Point> days) {}

  public record Effect(
      UUID entryId,
      String label,
      FinancialEntry.Type type,
      String amount,
      LocalDate originalDate,
      LocalDate projectedDate,
      boolean outsideHorizon,
      String kind) {}

  public record Source(
      UUID entryId,
      String label,
      FinancialEntry.Type type,
      String amount,
      LocalDate dueDate,
      LocalDate projectedDate,
      boolean baselineIncluded,
      boolean scenarioIncluded,
      String kind) {}

  public record Explanation(
      LocalDate date,
      String baselineOpening,
      String scenarioOpening,
      String baselineIncome,
      String baselineExpense,
      String scenarioIncome,
      String scenarioExpense,
      String baselineBalance,
      String scenarioBalance,
      List<Source> sources,
      long totalSources,
      boolean truncated) {}

  public record Preview(
      Projection baseline,
      Projection scenario,
      String closingDelta,
      List<Effect> effects,
      Explanation explanation) {}

  private record Resolved(FinancialEntry entry, LocalDate from, LocalDate to) {}

  private final CurrentUser current;
  private final FinanceService finance;
  private final AccountRepository accounts;
  private final EntryRepository entries;
  private final ReportService reports;
  private final Clock clock;

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private String money(BigDecimal amount) {
    return amount.setScale(2).toPlainString();
  }

  private BigDecimal remaining(FinancialEntry e) {
    return e.getAmount().subtract(e.getPaidAmount());
  }

  private LocalDate effective(FinancialEntry e, LocalDate today) {
    return e.getDueDate().isBefore(today) ? today : e.getDueDate();
  }

  private void scope(int days, UUID accountId) {
    if (days < 1 || days > 366) throw invalid("Escolha um horizonte de 1 a 366 dias.");
    if (accountId != null
        && accounts.findByIdAndCompanyId(accountId, current.companyId()).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
  }

  private Specification<FinancialEntry> eligible(
      int days, UUID accountId, boolean overdue, LocalDate today) {
    UUID company = current.companyId();
    LocalDate end = today.plusDays(days);
    return (root, q, cb) -> {
      var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
      predicates.add(cb.equal(root.get("companyId"), company));
      predicates.add(
          root.get("status").in(FinancialEntry.Status.PENDING, FinancialEntry.Status.PARTIAL));
      predicates.add(cb.isNull(root.get("transferId")));
      predicates.add(cb.isFalse(root.get("seriesPaused")));
      predicates.add(
          cb.greaterThan(root.<BigDecimal>get("amount"), root.<BigDecimal>get("paidAmount")));
      predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), end));
      if (!overdue) predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), today));
      if (accountId != null) predicates.add(cb.equal(root.get("accountId"), accountId));
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  public FinanceService.EntryPage candidates(
      int days, UUID accountId, boolean overdue, String search, int page, int size) {
    scope(days, accountId);
    if (page < 0 || size < 1 || size > 50 || search != null && search.length() > 255)
      throw invalid("Consulta de lançamentos inválida.");
    var spec = eligible(days, accountId, overdue, LocalDate.now(clock));
    if (search != null && !search.isBlank()) {
      String term =
          "%"
              + search
                  .strip()
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("description")), term, '!'));
    }
    var pageData = entries.findAll(spec, PageRequest.of(page, size, Sort.by("dueDate", "id")));
    return new FinanceService.EntryPage(
        pageData.map(finance::view).getContent(),
        pageData.getTotalElements(),
        pageData.getTotalPages(),
        page);
  }

  private Projection projection(ReportService.Forecast f) {
    return new Projection(
        f.start(),
        f.end(),
        money(f.openingBalance()),
        money(f.closingBalance()),
        money(f.pendingIncome()),
        money(f.pendingExpense()),
        money(f.minimumBalance()),
        f.firstNegativeDate(),
        f.days().stream()
            .map(
                d -> new Point(d.date(), money(d.income()), money(d.expense()), money(d.balance())))
            .toList());
  }

  private void move(
      Map<LocalDate, BigDecimal[]> days,
      LocalDate date,
      FinancialEntry.Type type,
      BigDecimal amount) {
    var values = days.get(date);
    if (values != null) {
      int index = type == FinancialEntry.Type.INCOME ? 0 : 1;
      values[index] = values[index].add(amount);
    }
  }

  public Preview preview(Input input) {
    scope(input.days(), input.accountId());
    var baseline = reports.forecast(input.days(), input.accountId(), input.includeOverdue());
    LocalDate selected = input.explainDate() == null ? baseline.start() : input.explainDate();
    if (selected.isBefore(baseline.start()) || selected.isAfter(baseline.end()))
      throw invalid("Escolha um dia dentro da projeção.");
    var shifts = input.shifts() == null ? List.<Shift>of() : input.shifts();
    var hypotheses = input.hypotheses() == null ? List.<Hypothesis>of() : input.hypotheses();
    Map<LocalDate, BigDecimal[]> values = new TreeMap<>();
    baseline.days().forEach(d -> values.put(d.date(), new BigDecimal[] {d.income(), d.expense()}));
    Map<UUID, Resolved> resolved = new LinkedHashMap<>();
    List<Effect> effects = new ArrayList<>();
    var shiftedEntries =
        shifts.isEmpty()
            ? Map.<UUID, FinancialEntry>of()
            : entries
                .findByCompanyIdAndIdIn(
                    current.companyId(), shifts.stream().map(Shift::entryId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(FinancialEntry::getId, e -> e));
    for (var shift : shifts) {
      if (resolved.containsKey(shift.entryId()))
        throw invalid("Cada lançamento pode ter apenas um adiamento no cenário.");
      var e = shiftedEntries.get(shift.entryId());
      if (e == null)
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lançamento não encontrado.");
      if (!Set.of(FinancialEntry.Status.PENDING, FinancialEntry.Status.PARTIAL)
              .contains(e.getStatus())
          || e.getTransferId() != null
          || e.isSeriesPaused()
          || remaining(e).signum() <= 0
          || input.accountId() != null && !input.accountId().equals(e.getAccountId())
          || e.getDueDate().isAfter(baseline.end())
          || !input.includeOverdue() && e.getDueDate().isBefore(baseline.start()))
        throw invalid("Escolha um lançamento pendente incluído na projeção atual.");
      LocalDate from = effective(e, baseline.start()), to = from.plusDays(shift.delayDays());
      resolved.put(e.getId(), new Resolved(e, from, to));
      move(values, from, e.getType(), remaining(e).negate());
      move(values, to, e.getType(), remaining(e));
      effects.add(
          new Effect(
              e.getId(),
              e.getDescription(),
              e.getType(),
              money(remaining(e)),
              from,
              to,
              to.isAfter(baseline.end()),
              "DELAY"));
    }
    for (var h : hypotheses) {
      if (h.date().isBefore(baseline.start()) || h.date().isAfter(baseline.end()))
        throw invalid("A primeira ocorrência da hipótese deve estar dentro da projeção.");
      for (int i = 0; i < h.occurrences(); i++) {
        LocalDate date = h.date().plusMonths(i);
        move(values, date, h.type(), h.amount());
        effects.add(
            new Effect(
                null,
                h.label().strip(),
                h.type(),
                money(h.amount()),
                null,
                date,
                date.isAfter(baseline.end()),
                "HYPOTHESIS"));
      }
    }
    BigDecimal balance = baseline.openingBalance(),
        minimum = balance,
        income = BigDecimal.ZERO,
        expense = BigDecimal.ZERO;
    LocalDate negative = balance.signum() < 0 ? baseline.start() : null;
    List<Point> points = new ArrayList<>();
    for (var day : values.entrySet()) {
      BigDecimal in = day.getValue()[0], out = day.getValue()[1];
      income = income.add(in);
      expense = expense.add(out);
      balance = balance.add(in).subtract(out);
      minimum = minimum.min(balance);
      if (negative == null && balance.signum() < 0) negative = day.getKey();
      points.add(new Point(day.getKey(), money(in), money(out), money(balance)));
    }
    var scenario =
        new Projection(
            baseline.start(),
            baseline.end(),
            money(baseline.openingBalance()),
            money(balance),
            money(income),
            money(expense),
            money(minimum),
            negative,
            points);
    var original = projection(baseline);
    return new Preview(
        original,
        scenario,
        money(balance.subtract(baseline.closingBalance())),
        effects,
        explain(input, selected, original, scenario, resolved, effects));
  }

  private Explanation explain(
      Input input,
      LocalDate date,
      Projection baseline,
      Projection scenario,
      Map<UUID, Resolved> shifted,
      List<Effect> effects) {
    var ids =
        shifted.values().stream()
            .filter(s -> s.from().equals(date) || s.to().equals(date))
            .map(s -> s.entry().getId())
            .toList();
    var spec =
        eligible(input.days(), input.accountId(), input.includeOverdue(), baseline.start())
            .and(
                (r, q, cb) -> {
                  var due =
                      date.equals(baseline.start()) && input.includeOverdue()
                          ? cb.lessThanOrEqualTo(r.get("dueDate"), date)
                          : cb.equal(r.get("dueDate"), date);
                  return ids.isEmpty() ? due : cb.or(due, r.get("id").in(ids));
                });
    var page = entries.findAll(spec, PageRequest.of(0, 101, Sort.by("dueDate", "id")));
    var sources = new ArrayList<Source>();
    for (var e : page.getContent()) {
      LocalDate from = effective(e, baseline.start()),
          to = shifted.containsKey(e.getId()) ? shifted.get(e.getId()).to() : from;
      sources.add(
          new Source(
              e.getId(),
              e.getDescription(),
              e.getType(),
              money(remaining(e)),
              e.getDueDate(),
              to,
              from.equals(date),
              to.equals(date),
              "PENDING_ENTRY"));
    }
    var added =
        effects.stream()
            .filter(e -> e.kind().equals("HYPOTHESIS") && e.projectedDate().equals(date))
            .toList();
    for (var e : added)
      sources.add(
          new Source(
              null,
              e.label(),
              e.type(),
              e.amount(),
              null,
              e.projectedDate(),
              false,
              true,
              "HYPOTHESIS"));
    long total = page.getTotalElements() + added.size();
    int index = (int) java.time.temporal.ChronoUnit.DAYS.between(baseline.start(), date);
    var a = baseline.days().get(index);
    var b = scenario.days().get(index);
    return new Explanation(
        date,
        index == 0 ? baseline.openingBalance() : baseline.days().get(index - 1).balance(),
        index == 0 ? scenario.openingBalance() : scenario.days().get(index - 1).balance(),
        a.income(),
        a.expense(),
        b.income(),
        b.expense(),
        a.balance(),
        b.balance(),
        sources.stream().limit(100).toList(),
        total,
        total > 100);
  }
}
