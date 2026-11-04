package com.fintry.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.profiles.default=test")
class ScenarioIntegrationTests {
  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    Clock scenarioClock() {
      return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    }
  }

  @LocalServerPort int port;
  @org.springframework.beans.factory.annotation.Autowired com.fintry.auth.UserRepository users;
  static final AtomicLong DOCS = new AtomicLong(71000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final LocalDate today = LocalDate.of(2026, 10, 19);

  class Browser {
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    final String email = UUID.randomUUID() + "@example.com";
    String token, header;

    JsonNode call(String method, String path, Object body, int status) throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
      if (!method.equals("GET") && token != null) request.header(header, token);
      if (body != null) request.header("Content-Type", "application/json");
      var response =
          http.send(
              request
                  .method(
                      method,
                      body == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(status, response.statusCode(), response.body());
      return response.body().isBlank() ? null : json.readTree(response.body());
    }

    void csrf() throws Exception {
      var result = call("GET", "/auth/csrf", null, 200);
      token = result.get("token").asText();
      header = result.get("headerName").asText();
    }

    void login() throws Exception {
      csrf();
      call("POST", "/auth/login", Map.of("email", email, "password", "Senha-segura-123"), 200);
      csrf();
    }

    Browser signup() throws Exception {
      csrf();
      call(
          "POST",
          "/auth/register",
          Map.of(
              "name",
              "Pessoa cenários",
              "companyName",
              "Empresa cenários",
              "document",
              "" + DOCS.incrementAndGet(),
              "email",
              email,
              "password",
              "Senha-segura-123"),
          201);
      login();
      return this;
    }

    String account(String amount) throws Exception {
      return call(
              "POST",
              "/finance/accounts",
              Map.of("name", UUID.randomUUID().toString(), "initialBalance", amount),
              201)
          .get("id")
          .asText();
    }

    String entry(String account, String type, String amount, int days) throws Exception {
      return call(
              "POST",
              "/finance/entries",
              Map.of(
                  "accountId",
                  account,
                  "description",
                  "Movimento " + UUID.randomUUID(),
                  "category",
                  "Operação",
                  "type",
                  type,
                  "amount",
                  amount,
                  "dueDate",
                  today.plusDays(days).toString(),
                  "paid",
                  false),
              201)
          .get("id")
          .asText();
    }

    JsonNode preview(Map<String, Object> input) throws Exception {
      return call("POST", "/scenarios/preview", input, 200);
    }
  }

  Map<String, Object> input(int days) {
    return new HashMap<>(
        Map.of(
            "days", days, "includeOverdue", false, "shifts", List.of(), "hypotheses", List.of()));
  }

  Map<String, Object> hypothesis(String type, String amount, LocalDate date, int occurrences) {
    return Map.of(
        "label",
        "Hipótese de teste",
        "type",
        type,
        "amount",
        amount,
        "date",
        date.toString(),
        "occurrences",
        occurrences);
  }

  @Test
  void partialDelayUsesRemainingValueAndDoesNotChangeFinancialRecords() throws Exception {
    var b = new Browser().signup();
    var account = b.account("100.00");
    var income = b.entry(account, "INCOME", "500.00", 2);
    b.entry(account, "EXPENSE", "250.00", 4);
    b.call(
        "POST",
        "/finance/entries/" + income + "/pay",
        Map.of("amount", "100.00", "paymentDate", today.toString()),
        200);
    var before = b.call("GET", "/finance/entries/" + income, null, 200).toString();
    var body = input(7);
    body.put("accountId", account);
    body.put("shifts", List.of(Map.of("entryId", income, "delayDays", 10)));
    body.put("hypotheses", List.of(hypothesis("EXPENSE", "10.10", today.plusDays(1), 1)));
    body.put("explainDate", today.plusDays(2).toString());
    var result = b.preview(body);
    assertEquals("350.00", result.get("baseline").get("closingBalance").asText());
    assertEquals("-60.10", result.get("scenario").get("closingBalance").asText());
    assertEquals("-410.10", result.get("closingDelta").asText());
    assertEquals(
        today.plusDays(4).toString(), result.get("scenario").get("firstNegativeDate").asText());
    var source = result.get("explanation").get("sources").get(0);
    assertEquals("400.00", source.get("amount").asText());
    assertTrue(source.get("baselineIncluded").asBoolean());
    assertFalse(source.get("scenarioIncluded").asBoolean());
    assertEquals(before, b.call("GET", "/finance/entries/" + income, null, 200).toString());
    assertEquals(2, b.call("GET", "/finance/entries", null, 200).get("totalElements").asInt());
  }

  @Test
  void emptyScenarioMatchesExistingForecastAndDecimalSumsStayExact() throws Exception {
    var b = new Browser().signup();
    var a = b.account("0.10");
    b.entry(a, "INCOME", "0.20", 1);
    var body = input(30);
    var result = b.preview(body);
    assertEquals(result.get("baseline"), result.get("scenario"));
    assertEquals("0.30", result.get("scenario").get("closingBalance").asText());
    var forecast = b.call("GET", "/reports/forecast?days=30", null, 200);
    assertEquals(today.toString(), forecast.get("start").asText());
    assertEquals(
        0,
        new java.math.BigDecimal(result.get("baseline").get("closingBalance").asText())
            .compareTo(forecast.get("closingBalance").decimalValue()));
  }

  @Test
  void scopedCandidatesAndPreviewRejectForeignPaidCancelledAndDuplicateEntries() throws Exception {
    var a = new Browser().signup();
    var b = new Browser().signup();
    var bank = a.account("0");
    var other = b.account("0");
    var id = a.entry(bank, "INCOME", "10", 1);
    var foreign = b.entry(other, "INCOME", "10", 1);
    var body = input(30);
    body.put("accountId", other);
    a.call("POST", "/scenarios/preview", body, 404);
    body.remove("accountId");
    body.put("shifts", List.of(Map.of("entryId", foreign, "delayDays", 2)));
    a.call("POST", "/scenarios/preview", body, 404);
    body.put(
        "shifts",
        List.of(Map.of("entryId", id, "delayDays", 2), Map.of("entryId", id, "delayDays", 3)));
    a.call("POST", "/scenarios/preview", body, 400);
    body.put("shifts", List.of(Map.of("entryId", id, "delayDays", 2)));
    a.call("POST", "/finance/entries/" + id + "/cancel", null, 200);
    a.call("POST", "/scenarios/preview", body, 400);
    assertEquals(0, a.call("GET", "/scenarios/candidates", null, 200).get("totalElements").asInt());
    a.call("GET", "/scenarios/candidates?accountId=" + other, null, 404);
    var paid = a.entry(bank, "INCOME", "15", 1);
    a.call(
        "POST", "/finance/entries/" + paid + "/pay", Map.of("paymentDate", today.toString()), 200);
    body.put("shifts", List.of(Map.of("entryId", paid, "delayDays", 1)));
    a.call("POST", "/scenarios/preview", body, 400);
  }

  @Test
  void overdueIsExplicitAndMonthlyHypothesesKeepTheOriginalDayAnchor() throws Exception {
    var b = new Browser().signup();
    var bank = b.account("0");
    var id = b.entry(bank, "INCOME", "15", -10);
    assertEquals(
        0,
        b.call("GET", "/scenarios/candidates?includeOverdue=false", null, 200)
            .get("totalElements")
            .asInt());
    assertEquals(
        1,
        b.call("GET", "/scenarios/candidates?includeOverdue=true", null, 200)
            .get("totalElements")
            .asInt());
    var body = input(90);
    body.put("includeOverdue", true);
    body.put("shifts", List.of(Map.of("entryId", id, "delayDays", 2)));
    body.put("hypotheses", List.of(hypothesis("EXPENSE", "0.10", LocalDate.of(2026, 10, 31), 3)));
    var result = b.preview(body);
    var effects = result.get("effects");
    assertEquals(today.toString(), effects.get(0).get("originalDate").asText());
    assertEquals(today.plusDays(2).toString(), effects.get(0).get("projectedDate").asText());
    assertEquals("2026-11-30", effects.get(2).get("projectedDate").asText());
    assertEquals("2026-12-31", effects.get(3).get("projectedDate").asText());
    assertEquals("14.70", result.get("scenario").get("closingBalance").asText());
  }

  @Test
  void simulationIsAvailableToViewersButStillRequiresCsrfAndValidInputs() throws Exception {
    var b = new Browser().signup();
    var u = users.findByEmail(b.email).orElseThrow();
    u.setRole(com.fintry.auth.AppUser.Role.VIEWER);
    u.setAuthVersion(u.getAuthVersion() + 1);
    users.saveAndFlush(u);
    b.login();
    b.preview(input(30));
    b.call(
        "POST", "/finance/accounts", Map.of("name", "Não permitido", "initialBalance", "0"), 403);
    String token = b.token;
    b.token = null;
    b.call("POST", "/scenarios/preview", input(30), 403);
    b.token = token;
    var body = input(0);
    b.call("POST", "/scenarios/preview", body, 400);
    body = input(30);
    body.put("hypotheses", List.of(hypothesis("INCOME", "0.001", today, 1)));
    b.call("POST", "/scenarios/preview", body, 400);
    body.put("hypotheses", List.of(hypothesis("EXPENSE", "10", today.minusDays(1), 1)));
    b.call("POST", "/scenarios/preview", body, 400);
    body.put("hypotheses", List.of(hypothesis("EXPENSE", "10", today, 13)));
    b.call("POST", "/scenarios/preview", body, 400);
    body = input(30);
    body.put("explainDate", today.plusDays(40).toString());
    b.call("POST", "/scenarios/preview", body, 400);
    body = input(30);
    body.put("shifts", Arrays.asList((Object) null));
    b.call("POST", "/scenarios/preview", body, 400);
  }

  @Test
  void candidatesArePaginatedAndExplanationDeclaresTruncatedSources() throws Exception {
    var b = new Browser().signup();
    var bank = b.account("0");
    for (int i = 0; i < 102; i++) b.entry(bank, "INCOME", "0.10", 0);
    assertEquals(
        50, b.call("GET", "/scenarios/candidates?size=50", null, 200).get("content").size());
    assertEquals(
        2, b.call("GET", "/scenarios/candidates?size=50&page=2", null, 200).get("content").size());
    var result = b.preview(input(1));
    assertEquals("10.20", result.get("scenario").get("closingBalance").asText());
    assertEquals(100, result.get("explanation").get("sources").size());
    assertTrue(result.get("explanation").get("truncated").asBoolean());
    assertEquals(102, result.get("explanation").get("totalSources").asInt());
  }

  @Test
  void pausedRecurringEntriesAreExcludedFromForecastAndScenario() throws Exception {
    var b = new Browser().signup();
    var bank = b.account("0");
    var row =
        Map.of(
            "accountId",
            bank,
            "description",
            "Série",
            "category",
            "Operação",
            "type",
            "EXPENSE",
            "amount",
            "10",
            "dueDate",
            today.plusDays(2).toString(),
            "paid",
            false);
    var series =
        b.call(
                "POST",
                "/finance/series",
                Map.of(
                    "requestId",
                    UUID.randomUUID().toString(),
                    "kind",
                    "RECURRING",
                    "occurrences",
                    2,
                    "entry",
                    row),
                201)
            .get("id")
            .asText();
    assertEquals("20.00", b.preview(input(90)).get("baseline").get("pendingExpense").asText());
    b.call("POST", "/finance/series/" + series + "/pause", null, 200);
    assertEquals(
        0, b.call("GET", "/scenarios/candidates?days=90", null, 200).get("totalElements").asInt());
    assertEquals("0.00", b.preview(input(90)).get("baseline").get("pendingExpense").asText());
  }

  @Test
  void indicatorExplanationMatchesPartialPaymentsAndExcludesTransfersAndCancelledRows()
      throws Exception {
    var b = new Browser().signup();
    var bank = b.account("100");
    var id = b.entry(bank, "INCOME", "50", 0);
    b.call(
        "POST",
        "/finance/entries/" + id + "/pay",
        Map.of("paymentDate", today.toString(), "amount", "20"),
        200);
    b.call(
        "POST",
        "/finance/entries/" + id + "/pay",
        Map.of("paymentDate", today.toString(), "amount", "10"),
        200);
    var info = b.call("GET", "/reports/explanation?kind=INCOME&month=2026-10", null, 200);
    assertEquals("30.00", info.get("amount").asText());
    assertEquals(2, info.get("sources").size());
    var flow = b.call("GET", "/finance/summary?month=2026-10", null, 200).get("flow");
    assertEquals(
        0,
        new java.math.BigDecimal("30.00")
            .compareTo(flow.get(flow.size() - 1).get("result").decimalValue()));
    var other = b.account("0");
    b.call(
        "POST",
        "/finance/transfers",
        Map.of(
            "sourceId",
            bank,
            "destinationId",
            other,
            "amount",
            "15",
            "date",
            today.toString(),
            "description",
            "Transferência"),
        201);
    assertEquals(
        "30.00",
        b.call("GET", "/reports/explanation?kind=INCOME&month=2026-10", null, 200)
            .get("amount")
            .asText());
    assertEquals(
        "130.00",
        b.call("GET", "/reports/explanation?kind=BALANCE", null, 200).get("amount").asText());
    b.call("POST", "/finance/entries/" + id + "/cancel", null, 200);
    assertEquals(
        "0.00",
        b.call("GET", "/reports/explanation?kind=RESULT&month=2026-10", null, 200)
            .get("amount")
            .asText());
    b.call("GET", "/reports/explanation?kind=INCOME&month=bad", null, 400);
  }
}
