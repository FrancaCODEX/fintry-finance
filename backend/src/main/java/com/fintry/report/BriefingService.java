package com.fintry.report;

import com.fintry.finance.FinanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns figures that other services already calculate into a few plain sentences. It never writes
 * and never recalculates balances: the forecast and the monthly summary stay the single source.
 */
@Service
@RequiredArgsConstructor
public class BriefingService {
  public enum Tone {
    POSITIVE,
    ATTENTION,
    NEUTRAL
  }

  /** {@code page} and {@code action} are null when the sentence has no follow-up. */
  public record Message(Tone tone, String text, String page, String action) {}

  public record Briefing(List<Message> messages) {}

  static final int HORIZON_DAYS = 30;
  static final int LIMIT = 3;
  private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

  private final ReportService reports;
  private final FinanceService finance;
  private final Clock clock;

  public Briefing briefing() {
    if (finance.accounts().isEmpty()) {
      return new Briefing(
          List.of(
              new Message(
                  Tone.NEUTRAL,
                  "Cadastre a primeira conta para o Fintry começar a acompanhar o seu caixa.",
                  "accounts",
                  "Abrir contas")));
    }
    var month = YearMonth.now(clock.withZone(BUSINESS_ZONE));
    var summary = finance.summary(month);
    var forecast = reports.forecast(HORIZON_DAYS, null, false);
    var messages = new ArrayList<Message>();

    if (forecast.firstNegativeDate() != null) {
      messages.add(
          new Message(
              Tone.ATTENTION,
              "Mantidos os vencimentos atuais, o saldo fica negativo em "
                  + DAY.format(forecast.firstNegativeDate())
                  + ".",
              "scenarios",
              "Simular alternativas"));
    }

    boolean lateIncome = forecast.overdueIncome().signum() > 0;
    boolean lateExpense = forecast.overdueExpense().signum() > 0;
    if (lateIncome || lateExpense) {
      String text =
          lateIncome && lateExpense
              ? "Há "
                  + money(forecast.overdueIncome())
                  + " a receber e "
                  + money(forecast.overdueExpense())
                  + " a pagar com o vencimento ultrapassado."
              : lateIncome
                  ? money(forecast.overdueIncome()) + " a receber já passaram do vencimento."
                  : money(forecast.overdueExpense()) + " em contas a pagar estão vencidos.";
      messages.add(new Message(Tone.ATTENTION, text, "pending", "Ver vencidas"));
    }

    String current = monthName(month);
    if (summary.result().signum() < 0) {
      messages.add(
          new Message(
              Tone.ATTENTION,
              "Em "
                  + current
                  + ", as despesas pagas superam as receitas em "
                  + money(summary.result().negate())
                  + ".",
              "reports",
              "Abrir relatório"));
    } else if (growth(summary.income(), summary.previousIncome()) >= 1) {
      // A month in progress is only compared when it is already ahead: being behind a full
      // month halfway through says nothing useful.
      messages.add(
          new Message(
              Tone.POSITIVE,
              "As receitas de "
                  + current
                  + " já superam "
                  + monthName(month.minusMonths(1))
                  + " em "
                  + growth(summary.income(), summary.previousIncome())
                  + "%.",
              null,
              null));
    }

    if (forecast.pendingIncome().signum() > 0 || forecast.pendingExpense().signum() > 0) {
      messages.add(
          new Message(
              Tone.NEUTRAL,
              "Nos próximos "
                  + HORIZON_DAYS
                  + " dias estão previstos "
                  + money(forecast.pendingIncome())
                  + " em entradas e "
                  + money(forecast.pendingExpense())
                  + " em saídas; o saldo estimado é "
                  + money(forecast.closingBalance())
                  + ".",
              "forecast",
              "Abrir projeção"));
    }

    if (messages.isEmpty()) {
      messages.add(
          new Message(
              Tone.POSITIVE,
              "Tudo em dia: nenhum vencimento pendente nos próximos " + HORIZON_DAYS + " dias.",
              null,
              null));
    }
    return new Briefing(List.copyOf(messages.subList(0, Math.min(LIMIT, messages.size()))));
  }

  private static long growth(BigDecimal value, BigDecimal previous) {
    if (previous.signum() <= 0 || value.signum() <= 0) return 0;
    return value
        .subtract(previous)
        .multiply(BigDecimal.valueOf(100))
        .divide(previous, 0, RoundingMode.HALF_UP)
        .longValue();
  }

  private static String monthName(YearMonth month) {
    return month.getMonth().getDisplayName(TextStyle.FULL, BRAZIL);
  }

  private static String money(BigDecimal value) {
    return NumberFormat.getCurrencyInstance(BRAZIL).format(value);
  }
}
