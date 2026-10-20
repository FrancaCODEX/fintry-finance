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
    properties = {"spring.profiles.default=test", "fintry.mail.enabled=false"})
class PendingIntegrationTests {
  @TestConfiguration
  static class BusinessTime {
    @Bean
    @Primary
    Clock pendingClock() {
      return Clock.fixed(Instant.parse("2026-10-20T02:30:00Z"), ZoneOffset.UTC);
    }
  }

  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(40000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final LocalDate today = LocalDate.of(2026, 10, 19);
  final String password = "Senha-segura-123";

  class Browser {
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String token, header, email = UUID.randomUUID() + "@example.com";

    JsonNode request(String method, String path, Object body, int status) throws Exception {
      var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
      if (!method.equals("GET") && token != null) r.header(header, token);
      if (body != null) r.header("Content-Type", "application/json");
      var response =
          http.send(
              r.method(
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
      var r = request("GET", "/auth/csrf", null, 200);
      token = r.get("token").asText();
      header = r.get("headerName").asText();
    }

    Browser signup() throws Exception {
      csrf();
      request(
          "POST",
          "/auth/register",
          Map.of(
              "name",
              "Pessoa pendências",
              "companyName",
              "Empresa pendências",
              "document",
              "" + DOCS.incrementAndGet(),
              "email",
              email,
              "password",
              password),
          201);
      login();
      return this;
    }

    void login() throws Exception {
      csrf();
      request("POST", "/auth/login", Map.of("email", email, "password", password), 200);
      csrf();
    }

    String account(String name) throws Exception {
      return request("POST", "/finance/accounts", Map.of("name", name, "initialBalance", "0"), 201)
          .get("id")
          .asText();
    }

    Map<String, Object> input(
        String account, String description, String type, String amount, int days) {
      return new HashMap<>(
          Map.of(
              "accountId",
              account,
              "description",
              description,
              "category",
              "Operação",
              "type",
              type,
              "amount",
              amount,
              "dueDate",
              today.plusDays(days).toString(),
              "paid",
              false));
    }

    String entry(String account, String description, String type, String amount, int days)
        throws Exception {
      return request(
              "POST", "/finance/entries", input(account, description, type, amount, days), 201)
          .get("id")
          .asText();
    }

    JsonNode pending(String params) throws Exception {
      return request("GET", "/finance/pending" + (params.isBlank() ? "" : "?" + params), null, 200);
    }
  }

  void amount(String expected, JsonNode actual) {
    assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()));
  }

  JsonNode window(JsonNode page, String name) {
    for (var w : page.get("windows"))
      if (w.get("group").asText().equals(name)) return w.get("totals");
    throw new AssertionError(name);
  }

  @Test
  void windowsUseBrazilDateDisjointBoundariesAndTotalsBeyondCurrentPage() throws Exception {
    var b = new Browser().signup();
    String a = b.account("Banco");
    b.entry(a, "Vencida receita", "INCOME", "100", -2);
    b.entry(a, "Vencida despesa", "EXPENSE", "25", -1);
    b.entry(a, "Hoje", "INCOME", "60", 0);
    b.entry(a, "Amanhã", "EXPENSE", "30", 1);
    b.entry(a, "Limite 7", "INCOME", "15", 7);
    b.entry(a, "Limite 8", "EXPENSE", "40", 8);
    b.entry(a, "Limite 30", "INCOME", "70", 30);
    b.entry(a, "Além de 30", "EXPENSE", "90", 31);
    String paid = b.entry(a, "Já recebida", "INCOME", "500", 0);
    b.request(
        "POST",
        "/finance/entries/" + paid + "/pay",
        Map.of("paymentDate", LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()),
        200);
    String cancelled = b.entry(a, "Cancelada", "EXPENSE", "900", 1);
    b.request("POST", "/finance/entries/" + cancelled + "/cancel", null, 200);
    var all = b.pending("size=2");
    assertEquals(today.toString(), all.get("today").asText());
    assertEquals(8, all.get("totalElements").asInt());
    assertEquals(4, all.get("totalPages").asInt());
    assertEquals(2, all.get("content").size());
    amount("245", all.get("totals").get("income"));
    amount("185", all.get("totals").get("expense"));
    assertEquals("Vencida receita", all.get("content").get(0).get("description").asText());
    assertEquals(2, window(all, "OVERDUE").get("count").asInt());
    assertEquals(1, window(all, "TODAY").get("count").asInt());
    assertEquals(2, window(all, "NEXT7").get("count").asInt());
    assertEquals(2, window(all, "NEXT30").get("count").asInt());
    var upcoming = b.pending("group=UPCOMING");
    assertEquals(3, upcoming.get("totalElements").asInt());
    amount("75", upcoming.get("totals").get("income"));
    amount("30", upcoming.get("totals").get("expense"));
    var now = b.pending("group=TODAY");
    assertEquals(1, now.get("totalElements").asInt());
    assertEquals(2, window(now, "OVERDUE").get("count").asInt());
    amount("60", now.get("totals").get("income"));
    assertEquals(2, b.pending("group=NEXT30").get("totalElements").asInt());
    var income = b.pending("type=INCOME&group=ALL");
    assertEquals(4, income.get("totalElements").asInt());
    amount("0", income.get("totals").get("expense"));
    assertEquals(1, window(income, "NEXT7").get("count").asInt());
    assertEquals(
        "Além de 30", b.pending("page=3&size=2").get("content").get(1).get("description").asText());
  }

  @Test
  void ownershipReadonlyProfilesAndInvalidFiltersAreEnforced() throws Exception {
    var a = new Browser().signup();
    var b = new Browser().signup();
    String own = a.account("Conta A"), foreign = b.account("Conta B");
    a.entry(own, "Empresa A", "INCOME", "10", 0);
    b.entry(foreign, "Empresa B", "EXPENSE", "999", 0);
    assertEquals(1, a.pending("").get("totalElements").asInt());
    amount("10", a.pending("").get("totals").get("income"));
    a.request("GET", "/finance/pending?accountId=" + foreign, null, 404);
    new Browser().request("GET", "/finance/pending", null, 401);
    var viewer = new Browser();
    String link =
        a.request(
                "POST",
                "/users",
                Map.of("name", "Leitor", "email", viewer.email, "role", "VIEWER"),
                201)
            .get("link")
            .asText();
    viewer.csrf();
    viewer.request(
        "POST",
        "/auth/accept-invite",
        Map.of(
            "token", link.substring(link.indexOf('=') + 1), "name", "Leitor", "password", password),
        204);
    viewer.login();
    assertEquals(1, viewer.pending("").get("totalElements").asInt());
    String entry = viewer.pending("").get("content").get(0).get("id").asText();
    viewer.request(
        "POST",
        "/finance/entries/" + entry + "/pay",
        Map.of("paymentDate", LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()),
        403);
    for (var query :
        List.of(
            "page=-1",
            "size=0",
            "size=101",
            "group=UNKNOWN",
            "source=UNKNOWN",
            "type=UNKNOWN",
            "search=" + "a".repeat(256))) a.request("GET", "/finance/pending?" + query, null, 400);
    assertEquals(0, a.pending("search=inexistente").get("totalElements").asInt());
    amount("0", a.pending("search=inexistente").get("totals").get("income"));
  }

  @Test
  void seriesAndSaleReceivablesKeepTheirOriginalWorkflows() throws Exception {
    var b = new Browser().signup();
    String a = b.account("Banco");
    b.entry(a, "Avulsa", "EXPENSE", "10", 0);
    var series = new HashMap<String, String>();
    for (String kind : List.of("RECURRING", "INSTALLMENT")) {
      var s =
          b.request(
              "POST",
              "/finance/series",
              Map.of(
                  "requestId",
                  UUID.randomUUID(),
                  "kind",
                  kind,
                  "occurrences",
                  2,
                  "entry",
                  b.input(a, kind, "EXPENSE", "20", 0)),
              201);
      series.put(kind, s.get("id").asText());
      assertEquals(2, b.pending("source=" + kind).get("totalElements").asInt());
    }
    String p =
        b.request(
                "POST",
                "/products",
                Map.of(
                    "sku",
                    "PEND-01",
                    "name",
                    "Produto",
                    "unit",
                    "UN",
                    "price",
                    "15",
                    "cost",
                    "0",
                    "minimum",
                    "0",
                    "initialQuantity",
                    "1",
                    "active",
                    true),
                201)
            .get("id")
            .asText();
    String sale =
        b.request(
                "POST",
                "/sales",
                Map.of(
                    "requestId",
                    UUID.randomUUID(),
                    "items",
                    List.of(Map.of("productId", p, "quantity", "1", "unitPrice", "15"))),
                201)
            .get("id")
            .asText();
    b.request(
        "POST",
        "/sales/" + sale + "/confirm",
        Map.of("accountId", a, "dueDate", today.toString(), "paid", false),
        200);
    var singles = b.pending("source=SINGLE");
    assertEquals(2, singles.get("totalElements").asInt());
    boolean hasSale = false;
    for (var e : singles.get("content")) if (!e.get("saleId").isNull()) hasSale = true;
    assertTrue(hasSale);
    b.request("POST", "/finance/series/" + series.get("RECURRING") + "/stop", null, 200);
    assertEquals(0, b.pending("source=RECURRING").get("totalElements").asInt());
    b.request("POST", "/sales/" + sale + "/cancel", null, 200);
    assertEquals(1, b.pending("source=SINGLE").get("totalElements").asInt());
  }

  @Test
  void accountSearchAndLiteralWildcardsAffectAllIndicators() throws Exception {
    var b = new Browser().signup();
    String a = b.account("Principal"), other = b.account("Outra");
    b.entry(a, "Ajuste 100%", "INCOME", "10", 0);
    b.entry(a, "Ajuste 1000", "INCOME", "20", 0);
    b.entry(a, "Ref_A", "EXPENSE", "30", 1);
    b.entry(a, "RefBA", "EXPENSE", "40", 1);
    b.entry(other, "Ajuste 100%", "INCOME", "100", 0);
    var matched =
        b.pending(
            "accountId="
                + a
                + "&search="
                + URLEncoder.encode("100%", java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(1, matched.get("totalElements").asInt());
    amount("10", matched.get("totals").get("income"));
    assertEquals(1, window(matched, "TODAY").get("count").asInt());
    assertEquals(1, b.pending("accountId=" + a + "&search=%5F").get("totalElements").asInt());
    assertEquals(2, b.pending("accountId=" + a + "&type=EXPENSE").get("totalElements").asInt());
  }

  @Test
  void refreshAfterPaymentsAndCancellationUsesAllPagesWithoutWritingAudit() throws Exception {
    var b = new Browser().signup();
    String a = b.account("Banco");
    var ids = new ArrayList<String>();
    for (int i = 0; i < 25; i++) ids.add(b.entry(a, "Pendência " + i, "EXPENSE", "0.10", 0));
    var p = b.pending("page=1&size=20");
    assertEquals(25, p.get("totalElements").asInt());
    assertEquals(5, p.get("content").size());
    amount("2.50", p.get("totals").get("expense"));
    String dates =
        "start="
            + LocalDate.now(ZoneId.of("America/Sao_Paulo"))
            + "&end="
            + LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    long before = b.request("GET", "/audit?" + dates, null, 200).get("totalElements").asLong();
    b.pending("");
    b.pending("group=TODAY");
    assertEquals(
        before, b.request("GET", "/audit?" + dates, null, 200).get("totalElements").asLong());
    for (String id : ids.subList(20, 25))
      b.request(
          "POST",
          "/finance/entries/" + id + "/pay",
          Map.of("paymentDate", LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()),
          200);
    p = b.pending("page=1&size=20");
    assertEquals(20, p.get("totalElements").asInt());
    assertEquals(0, p.get("content").size());
    amount("2", p.get("totals").get("expense"));
    b.request("POST", "/finance/entries/" + ids.get(0) + "/cancel", null, 200);
    assertEquals(19, b.pending("").get("totalElements").asInt());
  }
}
