package com.fintry.finance;

import com.fintry.auth.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendingService {
  private final CurrentUser current;
  private final AccountRepository accounts;
  private final EntryRepository entries;
  private final FinanceService finance;
  private final EntityManager em;
  private final Clock clock;

  public enum Group {
    ALL,
    OVERDUE,
    TODAY,
    NEXT7,
    NEXT30,
    UPCOMING
  }

  public enum Source {
    ALL,
    SINGLE,
    RECURRING,
    INSTALLMENT
  }

  public record Totals(long count, BigDecimal income, BigDecimal expense) {
    Totals plus(Totals other) {
      return new Totals(count + other.count, income.add(other.income), expense.add(other.expense));
    }
  }

  public record Window(Group group, Totals totals) {}

  public record Result(
      List<FinanceService.EntryView> content,
      long totalElements,
      int totalPages,
      int number,
      LocalDate today,
      Group group,
      Totals totals,
      List<Window> windows) {}

  @Bean
  static Clock businessClock() {
    return Clock.system(ZoneId.of("America/Sao_Paulo"));
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private Predicate window(
      Group group, Root<FinancialEntry> root, CriteriaBuilder cb, LocalDate today) {
    return switch (group) {
      case ALL -> cb.conjunction();
      case OVERDUE -> cb.lessThan(root.get("dueDate"), today);
      case TODAY -> cb.equal(root.get("dueDate"), today);
      case NEXT7 -> cb.between(root.get("dueDate"), today.plusDays(1), today.plusDays(7));
      case NEXT30 -> cb.between(root.get("dueDate"), today.plusDays(8), today.plusDays(30));
      case UPCOMING -> cb.between(root.get("dueDate"), today, today.plusDays(7));
    };
  }

  private Totals totals(Object[] row, int offset) {
    return new Totals(
        ((Number) row[offset]).longValue(),
        (BigDecimal) row[offset + 1],
        (BigDecimal) row[offset + 2]);
  }

  private Map<Group, Totals> aggregate(Specification<FinancialEntry> spec, LocalDate today) {
    var cb = em.getCriteriaBuilder();
    var q = cb.createQuery(Object[].class);
    var root = q.from(FinancialEntry.class);
    var columns = new ArrayList<Selection<?>>();
    var groups = List.of(Group.ALL, Group.OVERDUE, Group.TODAY, Group.NEXT7, Group.NEXT30);
    for (var group : groups) {
      Predicate range = window(group, root, cb, today);
      var remaining = cb.diff(root.<BigDecimal>get("amount"), root.<BigDecimal>get("paidAmount"));
      Predicate outstanding = cb.greaterThan(remaining, BigDecimal.ZERO);
      columns.add(cb.coalesce(cb.sum(cb.<Long>selectCase().when(cb.and(range, outstanding), 1L).otherwise(0L)), 0L));
      for (var type : List.of(FinancialEntry.Type.INCOME, FinancialEntry.Type.EXPENSE))
        columns.add(
            cb.coalesce(
                cb.sum(
                    cb.<BigDecimal>selectCase()
                        .when(
                            cb.and(range, cb.equal(root.get("type"), type)),
                            remaining)
                        .otherwise(BigDecimal.ZERO)),
                BigDecimal.ZERO));
    }
    q.multiselect(columns).where(spec.toPredicate(root, q, cb));
    var row = em.createQuery(q).getSingleResult();
    var out = new EnumMap<Group, Totals>(Group.class);
    for (int i = 0; i < groups.size(); i++) out.put(groups.get(i), totals(row, i * 3));
    out.put(Group.UPCOMING, out.get(Group.TODAY).plus(out.get(Group.NEXT7)));
    return out;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Result list(
      Group group,
      FinancialEntry.Type type,
      UUID accountId,
      Source source,
      String search,
      int page,
      int size) {
    if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 255)
      throw invalid(
          "Confira os filtros e a paginação (até 100 por página). Busca limitada a 255"
              + " caracteres.");
    UUID company = current.companyId();
    if (accountId != null)
      accounts
          .findByIdAndCompanyId(accountId, company)
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
    String term =
        search == null || search.isBlank()
            ? null
            : "%"
                + search
                    .strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
    Specification<FinancialEntry> base =
        (root, q, cb) -> {
          var p = new ArrayList<Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          p.add(root.get("status").in(FinancialEntry.Status.PENDING, FinancialEntry.Status.PARTIAL));
          p.add(cb.isNull(root.get("transferId")));
          if (type != null) p.add(cb.equal(root.get("type"), type));
          if (accountId != null) p.add(cb.equal(root.get("accountId"), accountId));
          if (source == Source.SINGLE) p.add(cb.isNull(root.get("seriesId")));
          else if (source != Source.ALL)
            p.add(cb.equal(root.get("seriesKind"), FinancialSeries.Kind.valueOf(source.name())));
          if (term != null) p.add(cb.like(cb.lower(root.get("description")), term, '!'));
          return cb.and(p.toArray(Predicate[]::new));
        };
    var sums = aggregate(base, today);
    Specification<FinancialEntry> selected =
        base.and((root, q, cb) -> window(group, root, cb, today));
    var result =
        entries.findAll(
            selected,
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Sort.Order.asc("dueDate"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
    return new Result(
        result.stream().map(finance::view).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        today,
        group,
        sums.get(group),
        List.of(
            new Window(Group.OVERDUE, sums.get(Group.OVERDUE)),
            new Window(Group.TODAY, sums.get(Group.TODAY)),
            new Window(Group.NEXT7, sums.get(Group.NEXT7)),
            new Window(Group.NEXT30, sums.get(Group.NEXT30))));
  }
}
