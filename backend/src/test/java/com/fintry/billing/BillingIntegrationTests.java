package com.fintry.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.default=test",
      "fintry.mail.enabled=false",
      "fintry.saas.mode=COMMERCIAL",
      "fintry.billing.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
class BillingIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(91000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
  final String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
  @Autowired MockAsaas provider;
  @Autowired BillingConnectionRepository connections;
  @Autowired com.fintry.subscription.SubscriptionRepository subscriptions;
  @Autowired com.fintry.auth.UserRepository users;

  @TestConfiguration
  static class Config {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    MockAsaas mock() throws Exception {
      return new MockAsaas();
    }
  }

  static class MockAsaas extends AsaasClient implements AutoCloseable {
    final com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final JsonMapper json = JsonMapper.builder().build();
    final ConcurrentMap<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    final ConcurrentMap<String, String> owners = new ConcurrentHashMap<>();
    final AtomicInteger issued = new AtomicInteger(), customerCount = new AtomicInteger();
    volatile boolean failAfterCreate, maliciousUrl;

    MockAsaas() throws Exception {
      server.createContext(
          "/v3",
          ex -> {
            try {
              String path = ex.getRequestURI().getPath();
              String method = ex.getRequestMethod();
              String key = ex.getRequestHeaders().getFirst("access_token");
              if (key == null || !key.startsWith("fixture-")) {
                ex.sendResponseHeaders(401, -1);
                return;
              }
              Object out = Map.of();
              if (path.equals("/v3/customers")) {
                var input = json.readTree(ex.getRequestBody().readAllBytes());
                assertTrue(input.path("notificationDisabled").asBoolean());
                assertFalse(input.has("email"));
                out = Map.of("id", "cus_" + customerCount.incrementAndGet());
              } else if (path.equals("/v3/payments") && method.equals("POST")) {
                var input = json.readTree(ex.getRequestBody().readAllBytes());
                String id = "pay_" + issued.incrementAndGet();
                var p = new ConcurrentHashMap<String, Object>();
                for (String field :
                    List.of(
                        "customer",
                        "billingType",
                        "value",
                        "dueDate",
                        "description",
                        "externalReference"))
                  p.put(
                      field,
                      input.get(field).isNumber()
                          ? input.get(field).decimalValue()
                          : input.get(field).asText());
                p.put("id", id);
                p.put("status", "PENDING");
                p.put("netValue", "97.50");
                p.put("creditDate", LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString());
                p.put(
                    "invoiceUrl",
                    maliciousUrl ? "javascript:alert(1)" : "https://www.asaas.com/i/fixture");
                payments.put(id, p);
                owners.put(id, key);
                out = p;
                if (failAfterCreate) {
                  failAfterCreate = false;
                  ex.sendResponseHeaders(503, -1);
                  return;
                }
              } else if (path.equals("/v3/payments")) {
                String query =
                    URLDecoder.decode(
                        ex.getRequestURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
                String ref = query.split("externalReference=")[1].split("&")[0];
                var found =
                    payments.values().stream()
                        .filter(
                            p ->
                                ref.equals(p.get("externalReference"))
                                    && key.equals(owners.get(p.get("id"))))
                        .toList();
                out = Map.of("totalCount", found.size(), "data", found);
              } else {
                String id = path.split("/")[3];
                var p = payments.get(id);
                if (p == null || !key.equals(owners.get(id))) {
                  ex.sendResponseHeaders(404, -1);
                  return;
                }
                if (path.endsWith("/pixQrCode")) out = Map.of("payload", "000201-fixture-pix");
                else {
                  if (method.equals("DELETE")) p.put("deleted", true);
                  out = p;
                }
              }
              byte[] bytes = json.writeValueAsBytes(out);
              ex.getResponseHeaders().set("Content-Type", "application/json");
              ex.sendResponseHeaders(200, bytes.length);
              ex.getResponseBody().write(bytes);
            } catch (Throwable e) {
              ex.sendResponseHeaders(500, -1);
            } finally {
              ex.close();
            }
          });
      server.start();
    }

    @Override
    protected String base(BillingConnection.Environment environment) {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3";
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  class Browser {
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String contactId;
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
  }

  String key(Browser b) {
    return "fixture-" + b.email;
  }

  String token(Browser b) {
    return "webhook_" + b.email.replaceAll("[^A-Za-z0-9]", "");
  }

  JsonNode configure(Browser b, String env) throws Exception {
    return b.request(
        "PUT",
        "/billing/connection",
        Map.of("environment", env, "apiKey", key(b), "webhookToken", token(b)),
        200);
  }

  String entry(Browser b, String account) throws Exception {
    if (b.contactId == null)
      b.contactId =
          b.request(
                  "POST",
                  "/contacts",
                  Map.of(
                      "name",
                      "Cliente cobrança",
                      "kind",
                      "CUSTOMER",
                      "document",
                      "52998224725",
                      "active",
                      true),
                  201)
              .path("id")
              .asText();
    String contact = b.contactId;
    return b.request(
            "POST",
            "/finance/entries",
            Map.of(
                "accountId",
                account,
                "description",
                "Receita integrada",
                "category",
                "Serviços",
                "type",
                "INCOME",
                "amount",
                "100.00",
                "dueDate",
                today,
                "paid",
                false,
                "contactId",
                contact),
            201)
        .path("id")
        .asText();
  }

  JsonNode issue(Browser b, String entry, String method) throws Exception {
    return b.request("POST", "/billing", Map.of("entryId", entry, "method", method), 200);
  }

  Map<String, Object> payment(JsonNode charge) {
    return provider.payments.values().stream()
        .filter(p -> p.get("externalReference").equals("fintry-" + charge.path("id").asText()))
        .findFirst()
        .orElseThrow();
  }

  void event(Browser b, JsonNode config, String providerId, String secret, int expected)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/webhooks/asaas/"
                        + config.path("webhookId").asText()))
            .header("Content-Type", "application/json")
            .header("asaas-access-token", secret)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(
                        Map.of(
                            "id",
                            "evt_repeat",
                            "event",
                            "PAYMENT_RECEIVED",
                            "payment",
                            Map.of("id", providerId, "status", "RECEIVED", "value", 1)))))
            .build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(expected, response.statusCode(), response.body());
  }

  JsonNode detail(Browser b, String entry) throws Exception {
    return b.request("GET", "/finance/entries/" + entry, null, 200).path("entry");
  }

  @Test
  void sandboxAndCancellationNeverChangeBalancesOrLeakSecrets() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "SANDBOX");
    var account = b.account("Asaas teste");
    String entry = entry(b, account);
    provider.maliciousUrl = true;
    var c = issue(b, entry, "PIX");
    provider.maliciousUrl = false;
    assertEquals("PENDING", c.path("status").asText());
    assertTrue(c.path("invoiceUrl").isNull());
    assertEquals(c.path("id"), issue(b, entry, "PIX").path("id"));
    b.request("POST", "/finance/entries/" + entry + "/pay", Map.of("paymentDate", today), 400);
    b.request("POST", "/finance/entries/" + entry + "/cancel", Map.of(), 400);
    b.request(
        "PUT",
        "/billing/connection",
        Map.of("environment", "SANDBOX", "apiKey", key(b), "webhookToken", token(b)),
        400);
    assertEquals(
        "000201-fixture-pix",
        b.request("POST", "/billing/" + c.path("id").asText() + "/pix", Map.of(), 200)
            .path("payload")
            .asText());
    var p = payment(c);
    event(b, config, p.get("id").toString(), "invalid", 401);
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals(
        "PENDING",
        b.request("GET", "/billing", null, 200)
            .path("content")
            .get(0)
            .path("status")
            .asText()); // payload's forged status ignored
    p.put("status", "RECEIVED");
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PENDING", detail(b, entry).path("status").asText());
    assertEquals(
        "TEST_RECEIVED",
        b.request("GET", "/billing", null, 200).path("content").get(0).path("status").asText());
    assertEquals(
        0,
        b.request("GET", "/finance/accounts", null, 200)
            .get(0)
            .path("balance")
            .decimalValue()
            .signum());
    String body =
        b.request("GET", "/billing/connection", null, 200).toString()
            + b.request("GET", "/billing", null, 200);
    assertFalse(body.contains(key(b)));
    assertFalse(body.contains(token(b)));
    var saved =
        connections.findById(UUID.fromString(config.path("webhookId").asText())).orElseThrow();
    assertFalse(saved.getApiKey().contains(key(b)));
    var second = entry(b, account);
    var boleto = issue(b, second, "BOLETO");
    assertEquals(
        "CANCELLED",
        b.request("POST", "/billing/" + boleto.path("id").asText() + "/cancel", Map.of(), 200)
            .path("status")
            .asText());
    b.request("POST", "/finance/entries/" + second + "/pay", Map.of("paymentDate", today), 200);
    var prod = configure(b, "PRODUCTION");
    assertNotEquals(config.path("webhookId"), prod.path("webhookId"));
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PENDING", detail(b, entry).path("status").asText());
  }

  @Test
  void productionSettlesOnceWithSeparateFeeAndFlagsRefund() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "PRODUCTION");
    String account = b.account("Saldo Asaas");
    String entry = entry(b, account);
    var c = issue(b, entry, "BOLETO");
    var p = payment(c);
    p.put("status", "CONFIRMED");
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PENDING", detail(b, entry).path("status").asText());
    p.put("status", "RECEIVED");
    event(b, config, p.get("id").toString(), token(b), 204);
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PAID", detail(b, entry).path("status").asText());
    var result = b.request("GET", "/billing", null, 200).path("content").get(0);
    assertEquals("RECEIVED", result.path("status").asText());
    assertEquals(
        0,
        new java.math.BigDecimal("97.50")
            .compareTo(
                b.request("GET", "/finance/accounts", null, 200)
                    .get(0)
                    .path("balance")
                    .decimalValue()));
    assertEquals(2, b.request("GET", "/finance/entries", null, 200).path("totalElements").asInt());
    b.request(
        "POST",
        "/finance/entries/" + result.path("feeEntryId").asText() + "/cancel",
        Map.of(),
        400);
    p.put("status", "REFUNDED");
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals(
        "REVIEW",
        b.request("GET", "/billing", null, 200).path("content").get(0).path("status").asText());
    assertEquals("PAID", detail(b, entry).path("status").asText());
  }

  @Test
  void uncertainCreationConcurrentRetryAndTenantIsolation() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "PRODUCTION");
    String entry = entry(b, b.account("Asaas"));
    int before = provider.issued.get();
    provider.failAfterCreate = true;
    var pool = Executors.newFixedThreadPool(2);
    try {
      var one = pool.submit(() -> issue(b, entry, "PIX"));
      var two = pool.submit(() -> issue(b, entry, "PIX"));
      var a = one.get(30, TimeUnit.SECONDS);
      var c = two.get(30, TimeUnit.SECONDS);
      assertEquals(a.path("id"), c.path("id"));
      assertEquals(before + 1, provider.issued.get());
      assertEquals(
          "PENDING",
          b.request("POST", "/billing/" + a.path("id").asText() + "/refresh", Map.of(), 200)
              .path("status")
              .asText());
      assertEquals(before + 1, provider.issued.get());
      var other = new Browser().signup();
      var otherConfig = configure(other, "SANDBOX");
      other.request("POST", "/billing/" + a.path("id").asText() + "/refresh", Map.of(), 404);
      other.request("POST", "/billing", Map.of("entryId", entry, "method", "PIX"), 404);
      var p = payment(a);
      p.put("status", "RECEIVED");
      event(other, otherConfig, p.get("id").toString(), token(other), 204);
      assertEquals("PENDING", detail(b, entry).path("status").asText());
      p.put("value", "101.00");
      event(b, config, p.get("id").toString(), token(b), 204);
      assertEquals("PENDING", detail(b, entry).path("status").asText());
      assertEquals(
          "REVIEW",
          b.request("GET", "/billing", null, 200).path("content").get(0).path("status").asText());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void rolesAndCsrfProtectBilling() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "SANDBOX");
    String validToken = b.token;
    b.token = null;
    b.request(
        "POST", "/billing", Map.of("entryId", UUID.randomUUID().toString(), "method", "PIX"), 403);
    b.token = validToken;
    var account = b.account("Asaas");
    String entry = entry(b, account);
    var user = users.findByEmail(b.email).orElseThrow();
    user.setRole(com.fintry.auth.AppUser.Role.FINANCE);
    users.save(user);
    b.login();
    b.request(
        "PUT",
        "/billing/connection",
        Map.of("environment", "SANDBOX", "apiKey", key(b), "webhookToken", token(b)),
        403);
    issue(b, entry, "PIX");
    user.setRole(com.fintry.auth.AppUser.Role.VIEWER);
    users.save(user);
    b.login();
    b.request("GET", "/billing", null, 200);
    b.request("POST", "/billing", Map.of("entryId", entry, "method", "PIX"), 403);
    String csrf = b.token;
    b.token = null;
    b.request("POST", "/billing", Map.of("entryId", entry, "method", "PIX"), 403);
    b.token = csrf;
  }

  @Test
  void invalidCreditDataAndForeignResourceNeverSettle() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "PRODUCTION");
    String account = b.account("Asaas revisão");
    String entry = entry(b, account);
    var c = issue(b, entry, "PIX");
    var p = payment(c);
    p.put("status", "RECEIVED");
    for (String net : List.of("-1.00", "101.00", "97.501")) {
      p.put("netValue", net);
      event(b, config, p.get("id").toString(), token(b), 204);
      assertEquals("PENDING", detail(b, entry).path("status").asText());
    }
    p.put("netValue", "97.50");
    for (String date :
        List.of("bad-date", LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).toString())) {
      p.put("creditDate", date);
      event(b, config, p.get("id").toString(), token(b), 204);
      assertEquals("PENDING", detail(b, entry).path("status").asText());
    }
    p.put("creditDate", today);
    p.put("customer", "cus_foreign");
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PENDING", detail(b, entry).path("status").asText());
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).path("totalElements").asInt());
    assertEquals(
        0,
        b.request("GET", "/finance/accounts", null, 200)
            .get(0)
            .path("balance")
            .decimalValue()
            .signum());
  }

  @Test
  void restoredCancelledChargeCannotPayAnAlteredEntry() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "PRODUCTION");
    String account = b.account("Asaas cancelamento");
    String entry = entry(b, account);
    var c = issue(b, entry, "BOLETO");
    var p = payment(c);
    b.request("POST", "/billing/" + c.path("id").asText() + "/cancel", Map.of(), 200);
    String another = b.account("Banco após cancelamento");
    b.request(
        "PUT",
        "/finance/entries/" + entry,
        Map.of(
            "accountId",
            another,
            "description",
            "Receita corrigida",
            "category",
            "Serviços",
            "type",
            "INCOME",
            "amount",
            "100.00",
            "dueDate",
            today,
            "paid",
            false,
            "contactId",
            b.contactId),
        200);
    p.put("deleted", false);
    p.put("status", "RECEIVED");
    event(b, config, p.get("id").toString(), token(b), 204);
    event(b, config, p.get("id").toString(), token(b), 204);
    b.request("POST", "/billing/" + c.path("id").asText() + "/refresh", Map.of(), 200);
    assertEquals("PENDING", detail(b, entry).path("status").asText());
    assertEquals(
        "REVIEW",
        b.request("GET", "/billing", null, 200).path("content").get(0).path("status").asText());
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).path("totalElements").asInt());
  }

  @Test
  void realProviderReceiptStillSettlesWhenSaasTrialHasExpired() throws Exception {
    var b = new Browser().signup();
    var config = configure(b, "PRODUCTION");
    String account = b.account("Saldo Asaas após teste");
    String entry = entry(b, account);
    var c = issue(b, entry, "PIX");
    var p = payment(c);
    var user = users.findByEmail(b.email).orElseThrow();
    var sub = subscriptions.findById(user.getCompanyId()).orElseThrow();
    sub.setTrialEndsAt(Instant.now().minusSeconds(1));
    subscriptions.saveAndFlush(sub);
    b.request(
        "POST",
        "/finance/accounts",
        Map.of("name", "Operação bloqueada", "initialBalance", 0),
        402);
    p.put("status", "RECEIVED");
    event(b, config, p.get("id").toString(), token(b), 204);
    assertEquals("PAID", detail(b, entry).path("status").asText());
    b.request("POST", "/billing/" + c.path("id").asText() + "/refresh", Map.of(), 200);
    assertEquals(
        0,
        new java.math.BigDecimal("97.50")
            .compareTo(
                b.request("GET", "/finance/accounts", null, 200)
                    .get(0)
                    .path("balance")
                    .decimalValue()));
  }

  @Test
  void encryptedSecretsAreBoundToTenantAndPurpose() {
    var s = new BillingSecrets("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    var company = UUID.randomUUID();
    String encrypted = s.encrypt("private", company, "api");
    assertEquals("private", s.decrypt(encrypted, company, "api"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> s.decrypt(encrypted, UUID.randomUUID(), "api"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> s.decrypt(encrypted, company, "webhook"));
    assertFalse(new BillingSecrets("").available());
  }
}
