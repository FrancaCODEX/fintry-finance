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
class PlanningIntegrationTests {
  @TestConfiguration
  static class BusinessTime {
    @Bean
    @Primary
    Clock pendingClock() {
      return Clock.fixed(Instant.parse("2026-10-20T02:30:00Z"), ZoneOffset.UTC);
    }
  }

  @LocalServerPort int port;

  @org.springframework.beans.factory.annotation.Autowired
  com.fintry.company.repository.CompanyRepository companies;

  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.transaction.PlatformTransactionManager transactions;

  static final AtomicLong DOCS = new AtomicLong(50000000000000L);
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

    HttpResponse<String> raw(String method, String path, Object body) throws Exception {
      var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
      if (token != null) r.header(header, token);
      if (body != null) r.header("Content-Type", "application/json");
      return http.send(
          r.method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    JsonNode pending(String params) throws Exception {
      return request("GET", "/finance/pending" + (params.isBlank() ? "" : "?" + params), null, 200);
    }
  }

  void amount(String expected, JsonNode actual) {
    assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()));
  }

  String category(Browser b, String name, String kind) throws Exception {
    return b.request("POST", "/categories", Map.of("name", name, "kind", kind, "active", true), 201)
        .get("id")
        .asText();
  }

  String budget(Browser b, String month, String category, String limit) throws Exception {
    return b.request(
            "POST",
            "/finance/budgets",
            Map.of("month", month, "categoryId", category, "limitAmount", limit),
            201)
        .asText();
  }

  JsonNode list(Browser b, String month) throws Exception {
    return b.request("GET", "/finance/budgets?month=" + month, null, 200);
  }

  String expense(Browser b, String account, String cat, String amount, boolean paid, LocalDate due)
      throws Exception {
    var data = b.input(account, "Despesa de teste", "EXPENSE", amount, 0);
    if (cat != null) data.put("categoryId", cat);
    data.put("dueDate", due);
    data.put("paid", paid);
    if (paid) data.put("paymentDate", today);
    return b.request("POST", "/finance/entries", data, 201).get("id").asText();
  }

  @Test
  void budgetsSeparatePaidPendingAndUncoveredWithDueDateBasis() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var c = category(b, "Operação", "EXPENSE");
    budget(b, "2026-10", c, "100");
    expense(b, a, c, "40", true, today);
    var pending = expense(b, a, c, "50", false, today);
    expense(b, a, null, "7", true, today);
    var uncovered = category(b, "Sem limite", "EXPENSE");
    expense(b, a, uncovered, "5", false, today);
    expense(b, a, c, "600", true, LocalDate.of(2026, 9, 30));
    expense(b, a, c, "800", false, LocalDate.of(2026, 11, 1));
    var cancelled = expense(b, a, c, "900", false, today);
    b.request("POST", "/finance/entries/" + cancelled + "/cancel", null, 200);
    b.entry(a, "Receita excluída", "INCOME", "700", 0);
    var dest = b.account("Caixa");
    b.request(
        "POST",
        "/finance/transfers",
        Map.of(
            "sourceId",
            a,
            "destinationId",
            dest,
            "amount",
            "20",
            "date",
            today,
            "description",
            "Transferência"),
        201);
    var result = list(b, "2026-10");
    amount("40", result.get("paid"));
    amount("50", result.get("pending"));
    amount("10", result.get("remaining"));
    amount("7", result.get("unbudgetedPaid"));
    amount("5", result.get("unbudgetedPending"));
    assertEquals("WARNING", result.get("content").get(0).get("status").asText());
    b.request("POST", "/finance/entries/" + pending + "/pay", Map.of("paymentDate", today), 200);
    result = list(b, "2026-10");
    amount("90", result.get("paid"));
    amount("0", result.get("pending"));
    amount("90", result.get("content").get(0).get("percentage"));
    expense(b, a, c, "10", false, today);
    assertEquals("WARNING", list(b, "2026-10").get("content").get(0).get("status").asText());
    expense(b, a, c, "0.01", false, today);
    result = list(b, "2026-10");
    assertEquals("EXCEEDED", result.get("content").get(0).get("status").asText());
    amount("-0.01", result.get("remaining"));
    var other = new Browser().signup();
    assertEquals(0, list(other, "2026-10").get("content").size());
  }

  @Test
  void budgetEditsArchiveAndDeletionPreserveFinancialDataAndAudit() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var c = category(b, "Escritório", "EXPENSE");
    var id = budget(b, "2026-10", c, "300");
    expense(b, a, c, "25", true, today);
    b.request(
        "PUT",
        "/categories/" + c,
        Map.of("name", "Escritório renomeado", "kind", "EXPENSE", "active", false),
        200);
    b.request(
        "PUT",
        "/finance/budgets/" + id,
        Map.of("month", "2026-10", "categoryId", c, "limitAmount", "30"),
        200);
    var result = list(b, "2026-10");
    assertEquals("Escritório renomeado", result.get("content").get(0).get("category").asText());
    assertFalse(result.get("content").get(0).get("active").asBoolean());
    amount("25", result.get("paid"));
    b.request(
        "POST",
        "/finance/budgets",
        Map.of("month", "2026-11", "categoryId", c, "limitAmount", "30"),
        400);
    b.request("DELETE", "/finance/budgets/" + id, null, 204);
    result = list(b, "2026-10");
    assertEquals(0, result.get("content").size());
    amount("25", result.get("unbudgetedPaid"));
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
    var audit =
        b.request("GET", "/audit?start=2026-10-01&end=2026-10-31&resource=BUDGET", null, 200);
    assertEquals(3, audit.get("totalElements").asInt());
    var onlyBudget = category(b, "Só orçamento", "EXPENSE");
    budget(b, "2026-10", onlyBudget, "10");
    b.request(
        "PUT",
        "/categories/" + onlyBudget,
        Map.of("name", "Só orçamento", "kind", "INCOME", "active", true),
        400);
  }

  @Test
  void copyPreviousMonthSkipsExistingAndInactiveAndCopiesOnlyLimits() throws Exception {
    var b = new Browser().signup();
    var a = category(b, "A", "EXPENSE");
    var c = category(b, "B", "BOTH");
    var inactive = category(b, "C", "EXPENSE");
    budget(b, "2026-09", a, "100");
    budget(b, "2026-09", c, "200");
    budget(b, "2026-09", inactive, "300");
    budget(b, "2026-10", a, "150");
    b.request(
        "PUT",
        "/categories/" + inactive,
        Map.of("name", "C", "kind", "EXPENSE", "active", false),
        200);
    var copied = b.request("POST", "/finance/budgets/copy", Map.of("month", "2026-10"), 200);
    assertEquals(1, copied.get("copied").asInt());
    assertEquals(2, copied.get("skipped").asInt());
    var result = list(b, "2026-10");
    amount("350", result.get("limitAmount"));
    amount("0", result.get("paid"));
    amount("0", result.get("pending"));
    assertEquals(
        0,
        b.request("POST", "/finance/budgets/copy", Map.of("month", "2026-10"), 200)
            .get("copied")
            .asInt());
    assertEquals(0, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
  }

  @Test
  void validatesMonthsAmountsCategoriesOwnershipAndViewerWrites() throws Exception {
    var b = new Browser().signup();
    var other = new Browser().signup();
    var c = category(b, "Despesa", "EXPENSE");
    var income = category(b, "Receita", "INCOME");
    var foreign = category(other, "Outra empresa", "EXPENSE");
    var id = budget(b, "2026-10", c, "100");
    for (var month : List.of("2026-13", "2026-1", "1999-12", "2101-01", "2026-10-01"))
      b.request("GET", "/finance/budgets?month=" + month, null, 400);
    for (var value : List.of("0", "-1", "1.001", "1000000000000"))
      b.request(
          "POST",
          "/finance/budgets",
          Map.of("month", "2026-11", "categoryId", c, "limitAmount", value),
          400);
    b.request(
        "POST",
        "/finance/budgets",
        Map.of("month", "2026-11", "categoryId", income, "limitAmount", "10"),
        400);
    b.request(
        "POST",
        "/finance/budgets",
        Map.of("month", "2026-11", "categoryId", foreign, "limitAmount", "10"),
        404);
    other.request(
        "PUT",
        "/finance/budgets/" + id,
        Map.of("month", "2026-11", "categoryId", foreign, "limitAmount", "10"),
        404);
    other.request("DELETE", "/finance/budgets/" + id, null, 404);
    b.request(
        "POST",
        "/finance/budgets",
        Map.of("month", "2026-10", "categoryId", c, "limitAmount", "10"),
        409);
    String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";
    var invite =
        b.request(
            "POST",
            "/users",
            Map.of("name", "Consulta", "email", viewerEmail, "role", "VIEWER"),
            201);
    var viewer = new Browser();
    viewer.csrf();
    viewer.request(
        "POST",
        "/auth/accept-invite",
        Map.of(
            "token",
            invite.get("link").asText().split("=")[1],
            "password",
            password,
            "name",
            "Consulta"),
        204);
    viewer.email = viewerEmail;
    viewer.login();
    assertEquals(1, list(viewer, "2026-10").get("content").size());
    viewer.request(
        "POST",
        "/finance/budgets",
        Map.of("month", "2026-11", "categoryId", c, "limitAmount", "10"),
        403);
    viewer.request("DELETE", "/finance/budgets/" + id, null, 403);
    viewer.request("POST", "/finance/budgets/copy", Map.of("month", "2026-11"), 403);
    viewer.request("POST", "/finance/sample", null, 403);
    var anon = new Browser();
    anon.request("GET", "/finance/budgets?month=2026-10", null, 401);
    anon.request("GET", "/finance/sample", null, 401);
    b.token = "invalid";
    b.request("DELETE", "/finance/budgets/" + id, null, 403);
  }

  @Test
  void concurrentBudgetCreationKeepsOneLimit() throws Exception {
    var b = new Browser().signup();
    var c = category(b, "Única", "EXPENSE");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var tasks =
          List.<java.util.concurrent.Callable<Integer>>of(
              () ->
                  b.raw(
                          "POST",
                          "/finance/budgets",
                          Map.of("month", "2026-10", "categoryId", c, "limitAmount", "100"))
                      .statusCode(),
              () ->
                  b.raw(
                          "POST",
                          "/finance/budgets",
                          Map.of("month", "2026-10", "categoryId", c, "limitAmount", "100"))
                      .statusCode());
      var codes = new ArrayList<Integer>();
      for (var f : pool.invokeAll(tasks)) codes.add(f.get());
      Collections.sort(codes);
      assertEquals(List.of(201, 409), codes);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, list(b, "2026-10").get("content").size());
  }

  @Test
  void samplePackIsAdditiveLinkedVariedAndHasReadableProofs() throws Exception {
    var b = new Browser().signup();
    var real = b.account("Minha conta real");
    var c = category(b, "[Exemplo] Aluguel", "EXPENSE");
    var original = expense(b, real, c, "42", true, today);
    assertFalse(b.request("GET", "/finance/sample", null, 200).get("loaded").asBoolean());
    b.request("POST", "/finance/sample", null, 201);
    assertEquals(4, b.request("GET", "/finance/accounts", null, 200).size());
    assertTrue(
        b.request("GET", "/finance/entries/" + original, null, 200)
            .get("entry")
            .get("description")
            .asText()
            .contains("Despesa de teste"));
    var contacts = b.request("GET", "/contacts?active=true", null, 200);
    assertEquals(5, contacts.get("totalElements").asInt());
    assertTrue(contacts.get("content").get(0).get("email").asText().endsWith("@example.invalid"));
    assertFalse(contacts.get("content").get(0).get("phone").asText().isBlank());
    assertEquals(6, b.request("GET", "/products?size=100", null, 200).get("totalElements").asInt());
    assertEquals(2, b.request("GET", "/finance/series", null, 200).get("totalElements").asInt());
    var sales = b.request("GET", "/sales", null, 200);
    assertEquals(4, sales.get("totalElements").asInt());
    var states = new HashSet<String>();
    int files = 0;
    for (var sale : sales.get("content")) {
      states.add(sale.get("status").asText());
      if (!sale.get("entryId").isNull()) {
        var entry = sale.get("entryId").asText();
        var attachments = b.request("GET", "/finance/entries/" + entry + "/attachments", null, 200);
        for (var file : attachments) {
          files++;
          var pdf =
              b.raw(
                  "GET",
                  "/finance/entries/" + entry + "/attachments/" + file.get("id").asText(),
                  null);
          assertEquals(200, pdf.statusCode());
          assertTrue(pdf.body().startsWith("%PDF-"));
        }
      }
    }
    assertEquals(Set.of("DRAFT", "CONFIRMED", "CANCELLED"), states);
    assertEquals(1, files);
    var p = b.pending("");
    assertTrue(p.get("totalElements").asInt() > 10);
    assertTrue(p.get("windows").get(0).get("totals").get("count").asInt() > 0);
    var budgets = list(b, "2026-10");
    assertEquals(5, budgets.get("content").size());
    assertTrue(budgets.get("warningCount").asInt() > 0);
    assertTrue(budgets.get("exceededCount").asInt() > 0);
    var before = b.request("GET", "/finance/summary?month=2026-10", null, 200);
    b.request("POST", "/finance/sample", null, 201);
    assertEquals(4, b.request("GET", "/finance/accounts", null, 200).size());
    assertEquals(before, b.request("GET", "/finance/summary?month=2026-10", null, 200));
    assertFalse(
        new Browser()
            .signup()
            .request("GET", "/finance/sample", null, 200)
            .get("loaded")
            .asBoolean());
  }

  @Test
  void concurrentSampleRequestsDoNotDuplicatePack() throws Exception {
    var b = new Browser().signup();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
    var held = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var company =
        UUID.fromString(b.request("GET", "/auth/me", null, 200).get("companyId").asText());
    try {
      var holder =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .execute(
                          tx -> {
                            companies.lock(company).orElseThrow();
                            held.countDown();
                            try {
                              if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("lock test timeout");
                            } catch (InterruptedException ex) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(ex);
                            }
                            return null;
                          }));
      assertTrue(held.await(10, java.util.concurrent.TimeUnit.SECONDS));
      var first = pool.submit(() -> b.raw("POST", "/finance/sample", null).statusCode());
      var second = pool.submit(() -> b.raw("POST", "/finance/sample", null).statusCode());
      // A slow first load must wait past H2's former two-second default, not fail with 500.
      Thread.sleep(2500);
      assertFalse(first.isDone(), "A solicitação deve aguardar o bloqueio da empresa.");
      assertFalse(second.isDone(), "A segunda solicitação deve aguardar sem duplicar.");
      release.countDown();
      holder.get(20, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(201, first.get(30, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(201, second.get(30, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    assertEquals(3, b.request("GET", "/finance/accounts", null, 200).size());
    assertEquals(4, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
  }
}
