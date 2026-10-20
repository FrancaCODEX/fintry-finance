package com.fintry.commerce;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.default=test", "fintry.mail.enabled=false"})
class CommerceIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCUMENTS = new AtomicLong(30000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
  final String password = "Senha-segura-123";

  class Browser {
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String token, header, email = UUID.randomUUID() + "@example.com", id;

    HttpResponse<String> raw(String method, String path, Object body, boolean withCsrf)
        throws Exception {
      var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
      if (withCsrf && token != null) r.header(header, token);
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

    JsonNode request(String method, String path, Object body, int status) throws Exception {
      var r = raw(method, path, body, true);
      assertEquals(status, r.statusCode(), r.body());
      return r.body().isBlank() ? null : json.readTree(r.body());
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
              "Operador comércio",
              "companyName",
              "Empresa comércio",
              "document",
              "" + DOCUMENTS.incrementAndGet(),
              "email",
              email,
              "password",
              password),
          201);
      return login(email);
    }

    Browser login(String address) throws Exception {
      email = address;
      csrf();
      id =
          request("POST", "/auth/login", Map.of("email", email, "password", password), 200)
              .get("id")
              .asText();
      csrf();
      return this;
    }

    String account() throws Exception {
      return request(
              "POST", "/finance/accounts", Map.of("name", "Banco", "initialBalance", "0"), 201)
          .get("id")
          .asText();
    }

    String product(String sku, String quantity) throws Exception {
      return request("POST", "/products", productInput(sku, quantity), 201).get("id").asText();
    }

    JsonNode product(String id) throws Exception {
      var rows = request("GET", "/products?active=&size=100", null, 200).get("content");
      for (var p : rows) if (p.get("id").asText().equals(id)) return p;
      throw new AssertionError("Produto ausente");
    }

    JsonNode draft(String product, String qty, String price) throws Exception {
      return request("POST", "/sales", draftInput(List.of(line(product, qty, price))), 201);
    }

    JsonNode summary() throws Exception {
      return request("GET", "/finance/summary?month=" + today.substring(0, 7), null, 200);
    }
  }

  Map<String, Object> productInput(String sku, String qty) {
    var p =
        new HashMap<String, Object>(
            Map.of(
                "sku",
                sku,
                "name",
                "Produto " + sku,
                "unit",
                "UN",
                "price",
                "10.00",
                "cost",
                "4.00",
                "minimum",
                "2",
                "active",
                true));
    if (qty != null) p.put("initialQuantity", qty);
    return p;
  }

  Map<String, Object> line(String product, String qty, String price) {
    return Map.of("productId", product, "quantity", qty, "unitPrice", price);
  }

  Map<String, Object> draftInput(List<Map<String, Object>> lines) {
    return new HashMap<>(
        Map.of("requestId", UUID.randomUUID(), "notes", "Venda de integração", "items", lines));
  }

  Map<String, Object> confirm(String account, boolean paid) {
    var p =
        new HashMap<String, Object>(Map.of("accountId", account, "dueDate", today, "paid", paid));
    if (paid) p.put("paymentDate", today);
    return p;
  }

  Map<String, Object> move(String product, String kind, String qty) {
    return new HashMap<>(
        Map.of(
            "requestId",
            UUID.randomUUID(),
            "productId",
            product,
            "kind",
            kind,
            "quantity",
            qty,
            "reason",
            "Contagem conferida"));
  }

  void amount(String expected, JsonNode actual) {
    assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()));
  }

  @Test
  void healthWorksWithoutSmtpWhenRecoveryIsDisabled() throws Exception {
    var b = new Browser();
    var response =
        b.http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("UP", json.readTree(response.body()).get("status").asText());
  }

  @Test
  void saleConnectsStockReceivablePaymentAndCancellationExactlyOnce() throws Exception {
    var b = new Browser().signup();
    String account = b.account(), p = b.product("CAM-01", "5");
    var draft = b.draft(p, "2", "12.50");
    String id = draft.get("id").asText();
    assertEquals("DRAFT", draft.get("status").asText());
    amount("25", draft.get("total"));
    amount("5", b.product(p).get("quantity"));
    amount("0", b.summary().get("pendingIncome"));
    var sale = b.request("POST", "/sales/" + id + "/confirm", confirm(account, false), 200);
    String entry = sale.get("entryId").asText();
    amount("3", b.product(p).get("quantity"));
    amount("25", b.summary().get("pendingIncome"));
    amount("0", b.summary().get("balance"));
    b.request("POST", "/sales/" + id + "/confirm", confirm(account, true), 200);
    amount("3", b.product(p).get("quantity"));
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
    assertEquals(
        id,
        b.request("GET", "/finance/entries/" + entry, null, 200)
            .get("entry")
            .get("saleId")
            .asText());
    b.request("POST", "/finance/entries/" + entry + "/cancel", null, 400);
    b.request(
        "PUT",
        "/finance/entries/" + entry,
        Map.of(
            "accountId",
            account,
            "description",
            "Não editar",
            "category",
            "Vendas",
            "type",
            "INCOME",
            "amount",
            "99",
            "dueDate",
            today,
            "paid",
            false),
        400);
    b.request("POST", "/finance/entries/" + entry + "/pay", Map.of("paymentDate", today), 200);
    amount("25", b.summary().get("balance"));
    assertEquals("PAID", b.request("GET", "/sales/" + id, null, 200).get("paymentStatus").asText());
    var changed = productInput("NOVO-SKU", null);
    changed.put("name", "Novo nome");
    changed.put("price", "100");
    changed.put("active", false);
    b.request("PUT", "/products/" + p, changed, 200);
    var history = b.request("GET", "/sales/" + id, null, 200).get("items").get(0);
    assertEquals("Produto CAM-01", history.get("productName").asText());
    amount("12.5", history.get("unitPrice"));
    b.request("POST", "/sales/" + id + "/cancel", null, 200);
    b.request("POST", "/sales/" + id + "/cancel", null, 200);
    amount("5", b.product(p).get("quantity"));
    amount("0", b.summary().get("balance"));
    assertEquals(
        3,
        b.request("GET", "/stock/movements?productId=" + p, null, 200)
            .get("totalElements")
            .asInt());
    assertEquals(
        "CANCELLED",
        b.request("GET", "/finance/entries/" + entry, null, 200)
            .get("entry")
            .get("status")
            .asText());
    b.request("POST", "/sales/" + id + "/confirm", confirm(account, false), 400);
    var audit =
        b.request("GET", "/audit?start=" + today + "&end=" + today + "&resource=SALE", null, 200);
    assertEquals(3, audit.get("totalElements").asInt());
  }

  @Test
  void manualMovementsPreventNegativeStockAndDuplicateRequests() throws Exception {
    var b = new Browser().signup();
    String p = b.product("EST-01", "1");
    var input = move(p, "IN", "4");
    b.request("POST", "/stock/movements", input, 200);
    b.request("POST", "/stock/movements", input, 200);
    amount("5", b.product(p).get("quantity"));
    input.put("quantity", "5");
    b.request("POST", "/stock/movements", input, 409);
    b.request("POST", "/stock/movements", move(p, "OUT", "6"), 400);
    amount("5", b.product(p).get("quantity"));
    b.request("POST", "/stock/movements", move(p, "SALE", "1"), 400);
    var edit = productInput("EST-01", null);
    edit.put("active", false);
    b.request("PUT", "/products/" + p, edit, 200);
    b.request("POST", "/stock/movements", move(p, "IN", "1"), 400);
    amount("5", b.product(p).get("quantity"));
    assertEquals(
        2,
        b.request("GET", "/stock/movements?productId=" + p, null, 200)
            .get("totalElements")
            .asInt());
    edit.put("active", true);
    b.request("PUT", "/products/" + p, edit, 200);
    b.request("POST", "/stock/movements", move(p, "OUT", "5"), 200);
    amount("0", b.product(p).get("quantity"));
    assertEquals(1, b.request("GET", "/products?low=true", null, 200).get("lowStockCount").asInt());
  }

  @Test
  void confirmationRollbackPreservesEveryStockAndAuditRecord() throws Exception {
    var b = new Browser().signup();
    String account = b.account(), a = b.product("A", "3"), z = b.product("Z", "0");
    String id =
        b.request(
                "POST", "/sales", draftInput(List.of(line(a, "2", "10"), line(z, "1", "10"))), 201)
            .get("id")
            .asText();
    b.request("POST", "/sales/" + id + "/confirm", confirm(account, true), 400);
    amount("3", b.product(a).get("quantity"));
    assertEquals(1, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
    assertEquals(0, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
    assertEquals("DRAFT", b.request("GET", "/sales/" + id, null, 200).get("status").asText());
    b.request("POST", "/stock/movements", move(z, "IN", "1"), 200);
    b.request(
        "POST", "/sales/" + id + "/confirm", confirm(UUID.randomUUID().toString(), false), 404);
    amount("3", b.product(a).get("quantity"));
    amount("1", b.product(z).get("quantity"));
    var badDate = confirm(account, true);
    badDate.put("paymentDate", LocalDate.parse(today).plusDays(1).toString());
    b.request("POST", "/sales/" + id + "/confirm", badDate, 400);
    assertEquals(2, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
    assertEquals(
        1,
        b.request("GET", "/audit?start=" + today + "&end=" + today + "&resource=SALE", null, 200)
            .get("totalElements")
            .asInt());
    b.request("POST", "/sales/" + id + "/confirm", confirm(account, true), 200);
    amount("1", b.product(a).get("quantity"));
    amount("0", b.product(z).get("quantity"));
    amount("30", b.summary().get("balance"));
  }

  @Test
  void concurrentSalesNeverOversellAndConcurrentCancelReturnsOnce() throws Exception {
    var b = new Browser().signup();
    var other = new Browser().login(b.email);
    String account = b.account(), p = b.product("ULTIMA", "1");
    String a = b.draft(p, "1", "10").get("id").asText(),
        z = b.draft(p, "1", "10").get("id").asText();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var tasks =
          List.<Callable<Integer>>of(
              () ->
                  b.raw("POST", "/sales/" + a + "/confirm", confirm(account, true), true)
                      .statusCode(),
              () ->
                  other
                      .raw("POST", "/sales/" + z + "/confirm", confirm(account, true), true)
                      .statusCode());
      var statuses = new ArrayList<Integer>();
      for (var f : pool.invokeAll(tasks)) statuses.add(f.get());
      Collections.sort(statuses);
      assertEquals(List.of(200, 400), statuses);
      amount("0", b.product(p).get("quantity"));
      amount("10", b.summary().get("balance"));
      String winner =
          b.request("GET", "/sales/" + a, null, 200).get("status").asText().equals("CONFIRMED")
              ? a
              : z;
      for (var f :
          pool.invokeAll(
              List.<Callable<Integer>>of(
                  () -> b.raw("POST", "/sales/" + winner + "/cancel", null, true).statusCode(),
                  () ->
                      other.raw("POST", "/sales/" + winner + "/cancel", null, true).statusCode())))
        assertEquals(200, f.get());
      amount("1", b.product(p).get("quantity"));
      amount("0", b.summary().get("balance"));
      assertEquals(3, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void paymentRacingCancellationNeverLeavesRevenueOrStockInconsistent() throws Exception {
    var owner = new Browser().signup();
    var cashier = new Browser().login(owner.email);
    String account = owner.account(), p = owner.product("RACE-PAY", "1");
    String sale = owner.draft(p, "1", "10").get("id").asText();
    String entry =
        owner
            .request("POST", "/sales/" + sale + "/confirm", confirm(account, false), 200)
            .get("entryId")
            .asText();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results =
          pool.invokeAll(
              List.<Callable<Integer>>of(
                  () ->
                      cashier
                          .raw(
                              "POST",
                              "/finance/entries/" + entry + "/pay",
                              Map.of("paymentDate", today),
                              true)
                          .statusCode(),
                  () -> owner.raw("POST", "/sales/" + sale + "/cancel", null, true).statusCode()));
      assertTrue(List.of(200, 400).contains(results.get(0).get()));
      assertEquals(200, results.get(1).get());
    } finally {
      pool.shutdownNow();
    }
    amount("1", owner.product(p).get("quantity"));
    amount("0", owner.summary().get("balance"));
    assertEquals(
        "CANCELLED",
        owner
            .request("GET", "/finance/entries/" + entry, null, 200)
            .get("entry")
            .get("status")
            .asText());
  }

  @Test
  void companyIsolationProfilesAndCsrfAlsoProtectCommerce() throws Exception {
    var a = new Browser().signup();
    var b = new Browser().signup();
    String account = a.account(), p = a.product("SKU-01", "2");
    b.product("SKU-01", "2");
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
    viewer.login(viewer.email);
    var draft = a.draft(p, "1", "10");
    String sale = draft.get("id").asText();
    viewer.request("GET", "/products", null, 200);
    viewer.request("GET", "/stock/movements", null, 200);
    viewer.request("GET", "/sales/" + sale, null, 200);
    viewer.request("POST", "/sales/" + sale + "/confirm", confirm(account, true), 403);
    viewer.request("POST", "/stock/movements", move(p, "IN", "1"), 403);
    viewer.request("PUT", "/products/" + p, productInput("SKU-01", null), 403);
    assertEquals(403, a.raw("POST", "/stock/movements", move(p, "IN", "1"), false).statusCode());
    b.request("GET", "/sales/" + sale, null, 404);
    b.request("POST", "/sales/" + sale + "/confirm", confirm(account, true), 404);
    b.request("POST", "/sales/" + sale + "/cancel", null, 404);
    b.request("POST", "/sales", draftInput(List.of(line(p, "1", "10"))), 404);
    b.request("POST", "/stock/movements", move(p, "IN", "1"), 404);
    b.request("GET", "/stock/movements?productId=" + p, null, 404);
    assertEquals(0, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
    a.request("POST", "/products", productInput("sku-01", "0"), 409);
    a.request("GET", "/products?page=-1", null, 400);
    a.request("GET", "/sales?size=101", null, 400);
  }

  @Test
  void decimalTotalsSnapshotsAndDraftRetriesStayConsistent() throws Exception {
    var b = new Browser().signup();
    String account = b.account();
    var product = productInput("FRAC", "2");
    product.put("unit", "KG");
    String p = b.request("POST", "/products", product, 201).get("id").asText();
    var input = draftInput(List.of(line(p, "0.005", "1.00")));
    var draft = b.request("POST", "/sales", input, 201);
    String id = draft.get("id").asText();
    amount("0.01", draft.get("total"));
    assertEquals(id, b.request("POST", "/sales", input, 200).get("id").asText());
    input.put("items", List.of(line(p, "0.1", "1")));
    b.request("POST", "/sales", input, 409);
    b.request("POST", "/sales", draftInput(List.of(line(p, "1", "10"), line(p, "1", "10"))), 400);
    b.request("POST", "/sales", draftInput(List.of(line(p, "0", "10"))), 400);
    b.request("POST", "/sales", draftInput(List.of(line(p, "0.0001", "10"))), 400);
    b.request("POST", "/sales", draftInput(List.of(line(p, "2", "999999999999.99"))), 400);
    b.request("POST", "/sales/" + id + "/confirm", confirm(account, true), 200);
    amount("1.995", b.product(p).get("quantity"));
    amount("0.01", b.summary().get("balance"));
    var edit = productInput("FRAC", null);
    edit.put("unit", "L");
    b.request("PUT", "/products/" + p, edit, 400);
    edit.put("unit", "KG");
    edit.put("initialQuantity", "5");
    b.request("PUT", "/products/" + p, edit, 400);
    String cancelled = b.draft(p, "0.5", "2").get("id").asText();
    b.request("POST", "/sales/" + cancelled + "/cancel", null, 200);
    amount("1.995", b.product(p).get("quantity"));
    assertEquals(2, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
  }
}
