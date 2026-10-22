package com.fintry.billing;

import static org.junit.jupiter.api.Assertions.*;

import com.fintry.subscription.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
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
      "fintry.saas.payments.enabled=true",
      "fintry.saas.payments.environment=PRODUCTION",
      "fintry.saas.payments.account=fixture-merchant",
      "fintry.saas.payments.api-key=fixture-saas",
      "fintry.saas.payments.webhook-token=fixture_saas_webhook_token_123456789",
      "fintry.saas.payments.pro=100.00",
      "fintry.saas.payments.essential=50.00"
    })
class SaasPaymentIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(93000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
  @Autowired BillingIntegrationTests.MockAsaas provider;
  @Autowired SubscriptionRepository subscriptions;
  @Autowired SaasInvoiceRepository invoices;
  @Autowired SaasPaymentsConfig config;
  @Autowired SaasRenewalService renewalService;
  @Autowired SaasRenewalRepository renewals;
  @Autowired com.fintry.auth.UserRepository users;
  @Autowired com.fintry.finance.EntryRepository entries;

  @TestConfiguration
  static class Config {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    BillingIntegrationTests.MockAsaas mock() throws Exception {
      return new BillingIntegrationTests.MockAsaas();
    }
  }

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
  }

  JsonNode view(Browser b) throws Exception {
    return b.request("GET", "/subscription", null, 200);
  }

  SaasSubscription entity(Browser b) throws Exception {
    return subscriptions
        .findById(UUID.fromString(view(b).path("companyId").asText()))
        .orElseThrow();
  }

  String planRequest(Browser b, String code) throws Exception {
    var v = view(b);
    var result =
        b.request(
            "POST",
            "/subscription/requests",
            Map.of("plan", code, "revision", v.path("revision").asLong()),
            200);
    return result.path("requests").get(0).path("id").asText();
  }

  Map<String, Object> input(String request, int price) {
    return Map.of(
        "requestId",
        request,
        "price",
        price,
        "method",
        "PIX",
        "payerName",
        "Pagador fixture",
        "document",
        "12345678901",
        "accepted",
        true);
  }

  JsonNode checkout(Browser b, String request) throws Exception {
    return b.request("POST", "/subscription/payments", input(request, 100), 200);
  }

  SaasInvoice invoice(JsonNode v) {
    return invoices.findById(UUID.fromString(v.path("id").asText())).orElseThrow();
  }

  Map<String, Object> payment(JsonNode v) {
    return provider.payments.get(invoice(v).getProviderId());
  }

  JsonNode refresh(Browser b, JsonNode i) throws Exception {
    return b.request(
        "POST", "/subscription/payments/" + i.path("id").asText() + "/refresh", Map.of(), 200);
  }

  void event(String token, String id, String reference, int expected) throws Exception {
    var b =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/webhooks/fintry-subscription/asaas"))
            .header("Content-Type", "application/json");
    if (token != null) b.header("asaas-access-token", token);
    var response =
        HttpClient.newHttpClient()
            .send(
                b.POST(
                        HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(
                                Map.of(
                                    "event",
                                    "PAYMENT_RECEIVED",
                                    "payment",
                                    Map.of(
                                        "id",
                                        id,
                                        "externalReference",
                                        reference,
                                        "status",
                                        "RECEIVED")))))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(expected, response.statusCode(), response.body());
  }

  @Test
  void explicitPriceConsentAuthorizationAndTenantIsolation() throws Exception {
    var b = new Browser().signup();
    String r = planRequest(b, "PRO");
    int before = provider.issued.get();
    b.request("POST", "/subscription/payments", input(r, 99), 409);
    var rejected = new HashMap<>(input(r, 100));
    rejected.put("accepted", false);
    b.request("POST", "/subscription/payments", rejected, 400);
    String token = b.token;
    b.token = null;
    b.request("POST", "/subscription/payments", input(r, 100), 403);
    b.token = token;
    var c = checkout(b, r);
    assertEquals("PENDING", c.path("status").asText());
    assertEquals(c.path("id"), checkout(b, r).path("id"));
    assertEquals(before + 1, provider.issued.get());
    var other = new Browser().signup();
    other.request(
        "POST", "/subscription/payments/" + c.path("id").asText() + "/refresh", Map.of(), 404);
    other.request("POST", "/subscription/payments", input(r, 100), 404);
    var user = users.findByEmail(other.email).orElseThrow();
    user.setRole(com.fintry.auth.AppUser.Role.FINANCE);
    users.saveAndFlush(user);
    other.login();
    other.request("POST", "/subscription/payments", input(r, 100), 403);
    assertEquals(SaasSubscription.State.TRIAL, entity(b).getState());
  }

  @Test
  void authoritativeWebhookActivationExpiryAndDuplicateDelivery() throws Exception {
    var b = new Browser().signup();
    var c = checkout(b, planRequest(b, "PRO"));
    var i = invoice(c);
    var s = entity(b);
    s.setTrialEndsAt(Instant.now().minusSeconds(1));
    subscriptions.saveAndFlush(s);
    long count = entries.count();
    event(null, i.getProviderId(), "fintry-saas-" + i.getId(), 401);
    event(config.getWebhookToken(), i.getProviderId(), "fintry-saas-" + i.getId(), 204);
    assertEquals(
        SaasSubscription.State.TRIAL,
        entity(b).getState()); // Untrusted payload cannot confirm payment.
    payment(c).put("status", "CONFIRMED");
    refresh(b, c);
    assertNull(entity(b).getPaidUntil());
    payment(c).put("status", "RECEIVED");
    event(config.getWebhookToken(), i.getProviderId(), "fintry-saas-" + i.getId(), 204);
    var paid = entity(b).getPaidUntil();
    assertTrue(paid.isAfter(Instant.now().plus(Duration.ofDays(29))));
    event(config.getWebhookToken(), i.getProviderId(), "fintry-saas-" + i.getId(), 204);
    refresh(b, c);
    assertEquals(paid, entity(b).getPaidUntil());
    assertEquals(count, entries.count());
    payment(c).put("status", "REFUNDED");
    assertEquals("REVIEW", refresh(b, c).path("status").asText());
    payment(c).put("status", "RECEIVED");
    refresh(b, c);
    assertEquals(paid, entity(b).getPaidUntil());
  }

  @Test
  void ambiguousIssueReconcilesAndCancelledRestorationNeverGrantsAccess() throws Exception {
    var b = new Browser().signup();
    String r = planRequest(b, "PRO");
    int before = provider.issued.get();
    provider.failAfterCreate = true;
    var c = checkout(b, r);
    assertEquals("REVIEW", c.path("status").asText());
    assertNull(invoice(c).getProviderId());
    checkout(b, r);
    assertEquals(before + 1, provider.issued.get());
    assertEquals("PENDING", refresh(b, c).path("status").asText());
    b.request(
        "POST",
        "/subscription/requests/" + r + "/cancel",
        Map.of("revision", view(b).path("revision").asLong()),
        409);
    b.request("POST", "/subscription/payments/" + c.path("id").asText() + "/cancel", Map.of(), 200);
    assertEquals("CANCELLED", refresh(b, c).path("status").asText());
    b.request(
        "POST",
        "/subscription/requests/" + r + "/cancel",
        Map.of("revision", view(b).path("revision").asLong()),
        200);
    payment(c).put("deleted", false);
    payment(c).put("status", "RECEIVED");
    assertEquals("REVIEW", refresh(b, c).path("status").asText());
    refresh(b, c);
    assertNull(entity(b).getPaidUntil());
  }

  @Test
  void webhookRecoversCreationRaceAndRequiresExactPaymentData() throws Exception {
    var b = new Browser().signup();
    provider.failAfterCreate = true;
    var c = checkout(b, planRequest(b, "PRO"));
    var local = invoice(c);
    assertNull(local.getProviderId());
    var remote =
        provider.payments.values().stream()
            .filter(p -> ("fintry-saas-" + local.getId()).equals(p.get("externalReference")))
            .findFirst()
            .orElseThrow();
    String remoteId = remote.get("id").toString();
    remote.put("status", "RECEIVED");
    remote.put("customer", "cus_divergent");
    event(config.getWebhookToken(), remoteId, "fintry-saas-" + local.getId(), 204);
    assertNull(entity(b).getPaidUntil());
    remote.put("customer", local.getCustomerId());
    event(config.getWebhookToken(), remoteId, "fintry-saas-" + local.getId(), 204);
    assertEquals(SaasSubscription.State.ACTIVE, entity(b).getState());
    assertEquals(remoteId, invoice(c).getProviderId());
  }

  @Test
  void sandboxMerchantChangesAndUnsafeLinksCannotActivate() throws Exception {
    config.setEnvironment(BillingConnection.Environment.SANDBOX);
    try {
      var b = new Browser().signup();
      var c = checkout(b, planRequest(b, "PRO"));
      payment(c).put("invoiceUrl", "https://asaas.com.evil.test/checkout");
      assertTrue(refresh(b, c).path("invoiceUrl").isNull());
      payment(c).put("status", "RECEIVED");
      assertEquals("TEST_PAID", refresh(b, c).path("status").asText());
      assertNull(entity(b).getPaidUntil());
      config.setEnvironment(BillingConnection.Environment.PRODUCTION);
      b.request(
          "POST", "/subscription/payments/" + c.path("id").asText() + "/refresh", Map.of(), 409);
    } finally {
      config.setEnvironment(BillingConnection.Environment.PRODUCTION);
    }
  }

  @Test
  void capacityDriftAndMismatchedPaymentsWaitForReview() throws Exception {
    var b = new Browser().signup();
    String request = planRequest(b, "ESSENTIAL");
    var c = b.request("POST", "/subscription/payments", input(request, 50), 200);
    for (int n = 0; n < 4; n++) b.account("Conta " + n);
    var p = payment(c);
    p.put("status", "RECEIVED");
    p.put("netValue", "49.00");
    assertEquals("REVIEW", refresh(b, c).path("status").asText());
    assertNull(entity(b).getPaidUntil());
    p.put("value", "51.00");
    assertEquals("REVIEW", refresh(b, c).path("status").asText());
    assertNull(entity(b).getPaidUntil());
  }

  @Test
  void concurrentCheckoutAndRenewalGrantOnlyOneThirtyDayPeriod() throws Exception {
    var b = new Browser().signup();
    var s = entity(b);
    s.setState(SaasSubscription.State.ACTIVE);
    s.setPaidUntil(Instant.now().plus(Duration.ofDays(10)));
    subscriptions.saveAndFlush(s);
    var prior = entity(b).getPaidUntil();
    String r = planRequest(b, "PRO");
    int before = provider.issued.get();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var one = executor.submit(() -> checkout(b, r));
      var two = executor.submit(() -> checkout(b, r));
      var c = one.get(30, TimeUnit.SECONDS);
      assertEquals(c.path("id"), two.get(30, TimeUnit.SECONDS).path("id"));
      assertEquals(before + 1, provider.issued.get());
      // The response of the losing reservation may still say ISSUING; consult the durable invoice.
      refresh(b, c);
      payment(c).put("status", "RECEIVED");
      refresh(b, c);
      refresh(b, c);
      assertEquals(prior.plus(Duration.ofDays(30)), entity(b).getPaidUntil());
    } finally {
      executor.shutdownNow();
    }
  }

  JsonNode paidInvoice(Browser b) throws Exception {
    var c = checkout(b, planRequest(b, "PRO"));
    payment(c).put("status", "RECEIVED");
    refresh(b, c);
    return c;
  }

  JsonNode renewal(Browser b) throws Exception {
    return b.request("GET", "/subscription/renewal", null, 200);
  }

  JsonNode authorize(Browser b, JsonNode invoice, int price, int status) throws Exception {
    return b.request(
        "POST",
        "/subscription/renewal/enable",
        Map.of(
            "sourceInvoiceId",
            invoice.path("id").asText(),
            "price",
            price,
            "revision",
            renewal(b).path("revision").asLong(),
            "accepted",
            true),
        status);
  }

  UUID nearEnd(Browser b) throws Exception {
    var s = entity(b);
    s.setPaidUntil(Instant.now().plus(Duration.ofDays(2)));
    subscriptions.saveAndFlush(s);
    return s.getCompanyId();
  }

  JsonNode lastInvoice(Browser b) throws Exception {
    return b.request("GET", "/subscription/payments", null, 200).path("invoices").get(0);
  }

  @Test
  void renewalNeedsReceiptConsentCurrentPriceCsrfAndAdmin() throws Exception {
    var b = new Browser().signup();
    var c = checkout(b, planRequest(b, "PRO"));
    authorize(b, c, 100, 409);
    payment(c).put("status", "RECEIVED");
    refresh(b, c);
    authorize(b, c, 99, 409);
    var data = new HashMap<String, Object>();
    data.put("sourceInvoiceId", c.path("id").asText());
    data.put("price", 100);
    data.put("revision", 0);
    data.put("accepted", false);
    b.request("POST", "/subscription/renewal/enable", data, 400);
    data.put("accepted", true);
    String token = b.token;
    b.token = null;
    b.request("POST", "/subscription/renewal/enable", data, 403);
    b.token = token;
    var other = new Browser().signup();
    authorize(other, c, 100, 404);
    assertTrue(authorize(b, c, 100, 200).path("enabled").asBoolean());
    var user = users.findByEmail(b.email).orElseThrow();
    user.setRole(com.fintry.auth.AppUser.Role.FINANCE);
    users.saveAndFlush(user);
    b.login();
    b.request(
        "POST",
        "/subscription/renewal/disable",
        Map.of("revision", renewal(b).path("revision").asLong()),
        403);
  }

  @Test
  void recurringCycleIsDurableConcurrentAndReusesPayerWithoutGrantOnIssue() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    assertFalse(renewalService.processCompany(entity(b).getCompanyId()));
    UUID company = nearEnd(b);
    var prior = entity(b).getPaidUntil();
    int issued = provider.issued.get(), customers = provider.customerCount.get();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> renewalService.processCompany(company));
      var second = executor.submit(() -> renewalService.processCompany(company));
      assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
    assertEquals(issued + 1, provider.issued.get());
    assertEquals(customers, provider.customerCount.get());
    assertEquals(prior, entity(b).getPaidUntil());
    var next = lastInvoice(b);
    assertEquals(prior, invoice(next).getRenewalPeriodEnd());
    assertEquals(invoice(c).getCustomerId(), invoice(next).getCustomerId());
    assertFalse(renewalService.processCompany(company));
    payment(next).put("status", "RECEIVED");
    refresh(b, next);
    refresh(b, next);
    assertEquals(prior.plus(Duration.ofDays(30)), entity(b).getPaidUntil());
    assertFalse(renewalService.processCompany(company));
  }

  @Test
  void newPriceNeedsFreshConsentAndCannotAlterAnExistingInvoice() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    UUID company = nearEnd(b);
    try {
      config.setPro(new java.math.BigDecimal("110.00"));
      assertFalse(renewalService.processCompany(company));
      assertEquals("PRICE_CHANGED", renewal(b).path("status").asText());
      authorize(b, c, 100, 409);
      authorize(b, c, 110, 200);
      assertTrue(renewalService.processCompany(company));
      assertEquals(
          0, invoice(lastInvoice(b)).getAmount().compareTo(new java.math.BigDecimal("110")));
      config.setPro(new java.math.BigDecimal("120.00"));
      assertFalse(renewalService.processCompany(company));
      assertEquals(
          0, invoice(lastInvoice(b)).getAmount().compareTo(new java.math.BigDecimal("110")));
    } finally {
      config.setPro(new java.math.BigDecimal("100.00"));
    }
  }

  @Test
  void stoppingRenewalsPreservesIssuedInvoiceAndPaidPeriod() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    UUID company = nearEnd(b);
    assertTrue(renewalService.processCompany(company));
    var next = lastInvoice(b);
    var before = entity(b).getPaidUntil();
    b.request(
        "POST",
        "/subscription/renewal/disable",
        Map.of("revision", renewal(b).path("revision").asLong()),
        200);
    assertFalse(renewalService.processCompany(company));
    assertEquals(before, entity(b).getPaidUntil());
    assertEquals(SaasInvoice.Status.PENDING, invoice(next).getStatus());
    payment(next).put("status", "RECEIVED");
    refresh(b, next);
    assertTrue(entity(b).isCancelAtPeriodEnd());
    assertEquals(before.plus(Duration.ofDays(30)), entity(b).getPaidUntil());
  }

  @Test
  void periodCancellationAlsoStopsRenewalsAndKeepDoesNotAuthorizeThem() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    nearEnd(b);
    b.request(
        "POST", "/subscription/cancel", Map.of("revision", view(b).path("revision").asLong()), 200);
    assertFalse(renewal(b).path("enabled").asBoolean());
    assertFalse(renewalService.processCompany(entity(b).getCompanyId()));
    b.request(
        "POST", "/subscription/keep", Map.of("revision", view(b).path("revision").asLong()), 200);
    assertFalse(renewal(b).path("enabled").asBoolean());
  }

  @Test
  void ambiguousRenewalNeverReissuesAndCancelledCycleRemainsClosed() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    UUID company = nearEnd(b);
    int before = provider.issued.get();
    provider.failAfterCreate = true;
    assertTrue(renewalService.processCompany(company));
    var next = lastInvoice(b);
    assertEquals("REVIEW", next.path("status").asText());
    assertFalse(renewalService.processCompany(company));
    assertEquals(before + 1, provider.issued.get());
    refresh(b, next);
    b.request(
        "POST", "/subscription/payments/" + next.path("id").asText() + "/cancel", Map.of(), 200);
    var request = invoice(next).getRequestId();
    b.request(
        "POST",
        "/subscription/requests/" + request + "/cancel",
        Map.of("revision", view(b).path("revision").asLong()),
        200);
    assertEquals("CYCLE_CLOSED", renewal(b).path("status").asText());
    b.request(
        "POST",
        "/subscription/renewal/disable",
        Map.of("revision", renewal(b).path("revision").asLong()),
        200);
    authorize(b, c, 100, 200);
    assertFalse(renewalService.processCompany(company));
    assertEquals(before + 1, provider.issued.get());
  }

  @Test
  void pendingRequestsConfigurationAndRefundPauseRecurrence() throws Exception {
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    UUID company = nearEnd(b);
    String pending = planRequest(b, "PRO");
    assertFalse(renewalService.processCompany(company));
    assertEquals("PENDING", renewal(b).path("status").asText());
    b.request(
        "POST",
        "/subscription/requests/" + pending + "/cancel",
        Map.of("revision", view(b).path("revision").asLong()),
        200);
    String account = config.getAccount();
    try {
      config.setAccount("other-merchant");
      assertFalse(renewalService.processCompany(company));
      assertEquals("CONFIGURATION", renewal(b).path("status").asText());
    } finally {
      config.setAccount(account);
    }
    payment(c).put("status", "REFUNDED");
    refresh(b, c);
    assertFalse(renewalService.processCompany(company));
    assertEquals("REVIEW", renewal(b).path("status").asText());
  }

  @Test
  void workerScansDueAgreementsAndBacksOffOtherCycles() throws Exception {
    for (var agreement : renewals.findAll()) {
      agreement.setNextCheckAt(Instant.now().plus(Duration.ofDays(365)));
      renewals.saveAndFlush(agreement);
    }
    var b = new Browser().signup();
    var c = paidInvoice(b);
    authorize(b, c, 100, 200);
    UUID company = nearEnd(b);
    int before = provider.issued.get();
    renewalService.processDue();
    assertEquals(before + 1, provider.issued.get());
    assertTrue(renewals.findById(company).orElseThrow().getNextCheckAt().isAfter(Instant.now()));
    renewalService.processDue();
    assertEquals(before + 1, provider.issued.get());
  }
}
