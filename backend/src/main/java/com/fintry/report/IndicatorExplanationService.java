package com.fintry.report;

import com.fintry.finance.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The formula and contributing payments are read in one consistent database snapshot. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class IndicatorExplanationService {
  public enum Kind {
    BALANCE,
    INCOME,
    EXPENSE,
    RESULT
  }

  public record Step(String label, String amount) {}

  public record Source(
      UUID entryId,
      UUID paymentId,
      String description,
      FinancialEntry.Type type,
      String amount,
      LocalDate paymentDate) {}

  public record Explanation(
      Kind kind,
      String amount,
      String formula,
      String scope,
      String capturedAt,
      List<Step> steps,
      List<Source> sources,
      long totalElements,
      int totalPages,
      int number) {}

  private final FinanceService finance;
  private final ReportService reports;
  private final Clock clock;

  private String money(BigDecimal value) {
    return value.setScale(2).toPlainString();
  }

  public Explanation explain(Kind kind, String reference, int page) {
    if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
    if (kind == Kind.BALANCE) {
      var accounts = finance.accounts();
      var balance =
          accounts.stream()
              .map(FinanceService.AccountView::balance)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      var initial =
          accounts.stream()
              .map(FinanceService.AccountView::initialBalance)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      return new Explanation(
          kind,
          money(balance),
          "Saldos iniciais + recebimentos − pagamentos, incluindo transferências em cada conta.",
          "Posição atual de todas as contas, independente do mês selecionado.",
          Instant.now(clock).toString(),
          List.of(
              new Step("Saldos iniciais", money(initial)),
              new Step("Movimentação líquida acumulada", money(balance.subtract(initial)))),
          List.of(),
          0,
          0,
          0);
    }
    YearMonth month;
    try {
      month = YearMonth.parse(reference);
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido. Use AAAA-MM.");
    }
    var type =
        kind == Kind.INCOME
            ? FinancialEntry.Type.INCOME
            : kind == Kind.EXPENSE ? FinancialEntry.Type.EXPENSE : null;
    var report =
        reports.report(
            new ReportService.Filters(
                month.atDay(1),
                month.atEndOfMonth(),
                ReportService.Basis.PAYMENT,
                FinancialEntry.Status.PAID,
                type,
                null,
                null,
                null),
            page,
            20);
    var totals = report.totals();
    var amount =
        kind == Kind.INCOME
            ? totals.income()
            : kind == Kind.EXPENSE ? totals.expense() : totals.result();
    var steps =
        kind == Kind.RESULT
            ? List.of(
                new Step("Receitas realizadas", money(totals.income())),
                new Step("Despesas realizadas (subtraídas)", money(totals.expense())))
            : List.of(
                new Step(
                    kind == Kind.INCOME ? "Recebimentos do período" : "Pagamentos do período",
                    money(amount)));
    return new Explanation(
        kind,
        money(amount),
        kind == Kind.RESULT
            ? "Receitas recebidas − despesas pagas. Resultado de caixa, não lucro contábil."
            : "Soma dos valores de cada baixa pela data de pagamento ou recebimento.",
        "Todas as contas · " + month + " · exclui transferências e lançamentos cancelados.",
        Instant.now(clock).toString(),
        steps,
        report.content().stream()
            .map(
                r ->
                    new Source(
                        r.id(),
                        r.paymentId(),
                        r.description(),
                        r.type(),
                        money(r.amount()),
                        r.paymentDate()))
            .toList(),
        report.totalElements(),
        report.totalPages(),
        report.number());
  }
}
