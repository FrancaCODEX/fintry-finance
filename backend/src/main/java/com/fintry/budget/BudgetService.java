package com.fintry.budget;

import static com.fintry.audit.AuditService.fields;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.*;
import com.fintry.finance.FinancialEntry;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final CategoryRepository categories;
  private final BudgetRepository budgets;
  private final EntityManager em;
  private final AuditService audit;

  public record Input(
      @NotBlank String month,
      @NotNull UUID categoryId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal limitAmount) {}

  public record CopyInput(@NotBlank String month) {}

  public record CopyResult(int copied, int skipped) {}

  public record Row(
      UUID id,
      UUID categoryId,
      String category,
      boolean active,
      BigDecimal limitAmount,
      BigDecimal paid,
      BigDecimal pending,
      BigDecimal committed,
      BigDecimal remaining,
      BigDecimal percentage,
      String status) {}

  public record Result(
      String month,
      List<Row> content,
      BigDecimal limitAmount,
      BigDecimal paid,
      BigDecimal pending,
      BigDecimal remaining,
      int warningCount,
      int exceededCount,
      BigDecimal unbudgetedPaid,
      BigDecimal unbudgetedPending) {}

  private ResponseStatusException invalid(String m) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Orçamento ou categoria não encontrado.");
  }

  private LocalDate month(String value) {
    try {
      if (value == null || !value.matches("[0-9]{4}-[0-9]{2}"))
        throw new DateTimeException("month");
      var m = YearMonth.parse(value);
      if (m.getYear() < 2000 || m.getYear() > 2100) throw new DateTimeException("range");
      return m.atDay(1);
    } catch (DateTimeException ex) {
      throw invalid("Informe um mês entre 2000-01 e 2100-12.");
    }
  }

  private UUID lockCompany() {
    var id = current.companyId();
    companies.lock(id).orElseThrow(this::missing);
    return id;
  }

  private FinancialCategory category(UUID id, UUID retained) {
    var c = categories.lock(id, current.companyId()).orElseThrow(this::missing);
    if (c.getKind() == FinancialCategory.Kind.INCOME)
      throw invalid("Use uma categoria de despesa ou mista.");
    if (!c.isActive() && !id.equals(retained)) throw invalid("Escolha uma categoria ativa.");
    return c;
  }

  private Map<String, String> details(CategoryBudget b) {
    return fields(
        "Mês",
        b.getMonth().toString().substring(0, 7),
        "Categoria",
        b.getCategoryId(),
        "Limite",
        b.getLimitAmount());
  }

  @Transactional
  public UUID save(UUID id, Input input) {
    var company = lockCompany();
    var date = month(input.month());
    var b =
        id == null
            ? new CategoryBudget()
            : budgets.findByIdAndCompanyId(id, company).orElseThrow(this::missing);
    var c = category(input.categoryId(), id == null ? null : b.getCategoryId());
    if ((id == null || !b.getCategoryId().equals(input.categoryId()) || !b.getMonth().equals(date))
        && budgets.existsByCompanyIdAndCategoryIdAndMonth(company, input.categoryId(), date))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Esta categoria já tem orçamento neste mês. Edite o limite existente.");
    var before = id == null ? Map.<String, String>of() : details(b);
    b.setCompanyId(company);
    b.setCategoryId(c.getId());
    b.setMonth(date);
    b.setLimitAmount(input.limitAmount().setScale(2, RoundingMode.UNNECESSARY));
    budgets.saveAndFlush(b);
    audit.change(
        id == null ? "CREATE" : "UPDATE",
        "BUDGET",
        b.getId(),
        c.getName() + " · " + input.month(),
        before,
        details(b));
    return b.getId();
  }

  @Transactional
  public void delete(UUID id) {
    lockCompany();
    var b = budgets.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
    var name =
        categories.findById(b.getCategoryId()).map(FinancialCategory::getName).orElse("Categoria");
    audit.change(
        "DELETE",
        "BUDGET",
        b.getId(),
        name + " · " + b.getMonth().toString().substring(0, 7),
        details(b),
        Map.of());
    budgets.delete(b);
  }

  @Transactional
  public CopyResult copy(CopyInput input) {
    var company = lockCompany();
    var target = month(input.month());
    int copied = 0, skipped = 0;
    for (var b : budgets.findByCompanyIdAndMonth(company, target.minusMonths(1))) {
      var c = categories.lock(b.getCategoryId(), company).orElseThrow(this::missing);
      if (!c.isActive()
          || c.getKind() == FinancialCategory.Kind.INCOME
          || budgets.existsByCompanyIdAndCategoryIdAndMonth(company, b.getCategoryId(), target)) {
        skipped++;
        continue;
      }
      save(null, new Input(input.month(), b.getCategoryId(), b.getLimitAmount()));
      copied++;
    }
    return new CopyResult(copied, skipped);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Result list(String value) {
    var date = month(value);
    var company = current.companyId();
    var plans = budgets.findByCompanyIdAndMonth(company, date);
    var cats = new HashMap<UUID, FinancialCategory>();
    for (var c : categories.findByCompanyIdOrderByNameAsc(company)) cats.put(c.getId(), c);
    var amounts =
        em.createQuery(
                "select e.categoryId,e.status,sum(e.amount) from FinancialEntry e where"
                    + " e.companyId=:company and e.type=:type and e.status in (:paid,:pending) and"
                    + " e.transferId is null and e.dueDate>=:start and e.dueDate<:end group by"
                    + " e.categoryId,e.status",
                Object[].class)
            .setParameter("company", company)
            .setParameter("type", FinancialEntry.Type.EXPENSE)
            .setParameter("paid", FinancialEntry.Status.PAID)
            .setParameter("pending", FinancialEntry.Status.PENDING)
            .setParameter("start", date)
            .setParameter("end", date.plusMonths(1))
            .getResultList();
    var paid = new HashMap<UUID, BigDecimal>();
    var pending = new HashMap<UUID, BigDecimal>();
    for (var a : amounts)
      (a[1] == FinancialEntry.Status.PAID ? paid : pending).put((UUID) a[0], (BigDecimal) a[2]);
    var rows = new ArrayList<Row>();
    BigDecimal total = BigDecimal.ZERO, used = BigDecimal.ZERO, planned = BigDecimal.ZERO;
    int warning = 0, exceeded = 0;
    for (var b : plans) {
      var p = paid.remove(b.getCategoryId());
      var n = pending.remove(b.getCategoryId());
      p = p == null ? BigDecimal.ZERO : p;
      n = n == null ? BigDecimal.ZERO : n;
      var committed = p.add(n);
      var percentage =
          committed
              .multiply(new BigDecimal("100"))
              .divide(b.getLimitAmount(), 2, RoundingMode.HALF_UP);
      var status =
          committed.compareTo(b.getLimitAmount()) > 0
              ? "EXCEEDED"
              : committed
                          .multiply(new BigDecimal("100"))
                          .compareTo(b.getLimitAmount().multiply(new BigDecimal("80")))
                      >= 0
                  ? "WARNING"
                  : "OK";
      if (status.equals("EXCEEDED")) exceeded++;
      else if (status.equals("WARNING")) warning++;
      var c = cats.get(b.getCategoryId());
      rows.add(
          new Row(
              b.getId(),
              b.getCategoryId(),
              c.getName(),
              c.isActive(),
              b.getLimitAmount(),
              p,
              n,
              committed,
              b.getLimitAmount().subtract(committed),
              percentage,
              status));
      total = total.add(b.getLimitAmount());
      used = used.add(p);
      planned = planned.add(n);
    }
    rows.sort(
        Comparator.comparing(Row::category, String.CASE_INSENSITIVE_ORDER).thenComparing(Row::id));
    return new Result(
        value,
        rows,
        total,
        used,
        planned,
        total.subtract(used).subtract(planned),
        warning,
        exceeded,
        paid.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
        pending.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
  }
}
