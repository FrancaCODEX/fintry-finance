package com.fintry.subscription;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
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
      "fintry.saas.mode=COMMERCIAL"
    })
class SubscriptionIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(92000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
  final String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
  @Autowired SubscriptionRepository subscriptions;
  @Autowired PlanRepository plans;
  @Autowired com.fintry.auth.UserRepository users;
  @Autowired com.fintry.attachment.AttachmentRepository attachments;
  @Autowired com.fintry.finance.EntryRepository entries;
  @Autowired com.fintry.company.repository.CompanyRepository companies;

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

  void active(Browser b, SaasPlan.Code plan) throws Exception {
    var s = entity(b);
    s.setPlan(plan);
    s.setState(SaasSubscription.State.ACTIVE);
    s.setPaidUntil(Instant.now().plus(Duration.ofDays(30)));
    subscriptions.saveAndFlush(s);
  }

  JsonNode request(Browser b, String plan, long revision, int status) throws Exception {
    return b.request(
        "POST", "/subscription/requests", Map.of("plan", plan, "revision", revision), status);
  }

  String entry(Browser b, String account) throws Exception {
    return b.request(
            "POST",
            "/finance/entries",
            Map.of(
                "accountId",
                account,
                "description",
                "Receita preservada",
                "category",
                "Serviços",
                "type",
                "INCOME",
                "amount",
                100,
                "dueDate",
                today,
                "paid",
                false),
            201)
        .path("id")
        .asText();
  }

  JsonNode invite(Browser b, String name, int status) throws Exception {
    return b.request(
        "POST",
        "/users",
        Map.of("name", name, "email", UUID.randomUUID() + "@example.test", "role", "VIEWER"),
        status);
  }

  @Test
  void trialCatalogHasNoInventedPricesOrAutomaticPaymentAndRequestDoesNotGrantAccess()
      throws Exception {
    var b = new Browser().signup();
    var v = view(b);
    assertEquals("TRIAL", v.path("status").asText());
    assertEquals("COMMERCIAL", v.path("mode").asText());
    assertEquals(3, v.path("plans").size());
    assertTrue(v.path("plans").get(0).path("monthlyPrice").isNull());
    assertTrue(
        Instant.parse(v.path("trialEndsAt").asText())
            .isAfter(Instant.now().plus(Duration.ofDays(13))));
    var result = request(b, "BUSINESS", v.path("revision").asLong(), 200);
    assertEquals("PRO", result.path("plan").asText());
    assertEquals("TRIAL", result.path("status").asText());
    assertEquals("PENDING", result.path("requests").get(0).path("status").asText());
    assertEquals(
        result.path("requests").get(0).path("id"),
        request(b, "BUSINESS", v.path("revision").asLong(), 200)
            .path("requests")
            .get(0)
            .path("id"));
    request(b, "ESSENTIAL", result.path("revision").asLong(), 409);
    b.request(
        "POST",
        "/subscription/simulate",
        Map.of("plan", "BUSINESS", "revision", result.path("revision").asLong()),
        403);
  }

  @Test
  void accountLimitIsAtomicAndSampleImportCannotBypassIt() throws Exception {
    var b = new Browser().signup();
    active(b, SaasPlan.Code.ESSENTIAL);
    b.account("Um");
    b.account("Dois");
    var pool = Executors.newFixedThreadPool(2);
    try {
      var tasks = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 2; i++) {
        final int n = i;
        tasks.add(
            pool.submit(
                () -> {
                  var req =
                      HttpRequest.newBuilder(
                              URI.create("http://localhost:" + port + "/api/v1/finance/accounts"))
                          .header(b.header, b.token)
                          .header("Content-Type", "application/json")
                          .POST(
                              HttpRequest.BodyPublishers.ofString(
                                  json.writeValueAsString(
                                      Map.of("name", "Concorrente " + n, "initialBalance", 0))))
                          .build();
                  return b.http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
                }));
      }
      var statuses =
          tasks.stream()
              .map(
                  f -> {
                    try {
                      return f.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .sorted()
              .toList();
      assertEquals(List.of(201, 409), statuses);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(3, view(b).path("usage").path("accounts").asInt());
    b.request("POST", "/finance/sample", Map.of(), 409);
    assertEquals(3, view(b).path("usage").path("accounts").asInt());
    assertEquals(0, b.request("GET", "/finance/entries", null, 200).path("totalElements").asInt());
  }

  @Test
  void invitationReservesSeatAndRevokingReleasesIt() throws Exception {
    var b = new Browser().signup();
    active(b, SaasPlan.Code.ESSENTIAL);
    invite(b, "Uma pessoa", 201);
    assertEquals(2, view(b).path("usage").path("members").asInt());
    invite(b, "Pessoa excedente", 409);
    var team = b.request("GET", "/users", null, 200).path("content");
    JsonNode invited = null;
    for (var u : team) if (u.path("invited").asBoolean()) invited = u;
    assertNotNull(invited);
    b.request("DELETE", "/users/" + invited.path("id").asText() + "/invitation", Map.of(), 204);
    assertEquals(1, view(b).path("usage").path("members").asInt());
    invite(b, "Nova pessoa", 201);
  }

  @Test
  void expiredTrialRetainsReadsExportsSecurityAndPlanRequestsButRejectsBusinessWrites()
      throws Exception {
    var b = new Browser().signup();
    String account = b.account("Banco");
    entry(b, account);
    var s = entity(b);
    s.setTrialEndsAt(Instant.now().minusSeconds(1));
    subscriptions.saveAndFlush(s);
    assertEquals("EXPIRED", view(b).path("status").asText());
    b.request("GET", "/finance/entries", null, 200);
    b.request("POST", "/finance/accounts", Map.of("name", "Bloqueada", "initialBalance", 0), 402);
    b.request("POST", "/finance/sample", Map.of(), 402);
    b.request("GET", "/auth/me", null, 200);
    invite(b, "Sem acesso novo", 402);
    request(b, "PRO", view(b).path("revision").asLong(), 200);
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).path("totalElements").asInt());
  }

  @Test
  void pendingRequestsHaveRevisionProtectionAndTenantIsolation() throws Exception {
    var b = new Browser().signup();
    var v = view(b);
    var x = request(b, "PRO", v.path("revision").asLong(), 200);
    String id = x.path("requests").get(0).path("id").asText();
    var other = new Browser().signup();
    other.request(
        "POST",
        "/subscription/requests/" + id + "/cancel",
        Map.of("revision", view(other).path("revision").asLong()),
        404);
    b.request(
        "POST",
        "/subscription/requests/" + id + "/cancel",
        Map.of("revision", v.path("revision").asLong()),
        409);
    b.request(
        "POST",
        "/subscription/requests/" + id + "/cancel",
        Map.of("revision", x.path("revision").asLong()),
        200);
    request(b, "ESSENTIAL", view(b).path("revision").asLong(), 200);
    var user = users.findByEmail(b.email).orElseThrow();
    user.setRole(com.fintry.auth.AppUser.Role.FINANCE);
    users.saveAndFlush(user);
    b.login();
    b.request("GET", "/subscription", null, 200);
    request(b, "BUSINESS", view(b).path("revision").asLong(), 403);
  }

  @Test
  void cancellationKeepsPaidPeriodAndEndsInReadOnlyWithoutDeletingData() throws Exception {
    var b = new Browser().signup();
    active(b, SaasPlan.Code.PRO);
    b.account("Preservada");
    var before = view(b);
    var result =
        b.request(
            "POST",
            "/subscription/cancel",
            Map.of("revision", before.path("revision").asLong()),
            200);
    assertTrue(result.path("writable").asBoolean());
    assertEquals(before.path("paidUntil"), result.path("paidUntil"));
    assertTrue(result.path("cancelAtPeriodEnd").asBoolean());
    result =
        b.request(
            "POST",
            "/subscription/keep",
            Map.of("revision", result.path("revision").asLong()),
            200);
    assertFalse(result.path("cancelAtPeriodEnd").asBoolean());
    result =
        b.request(
            "POST",
            "/subscription/cancel",
            Map.of("revision", result.path("revision").asLong()),
            200);
    var s = entity(b);
    s.setPaidUntil(Instant.now().minusSeconds(1));
    subscriptions.saveAndFlush(s);
    assertEquals("CANCELLED", view(b).path("status").asText());
    assertFalse(view(b).path("writable").asBoolean());
    assertEquals(1, b.request("GET", "/finance/accounts", null, 200).size());
  }

  @Test
  void legacyAccessIsNotCappedAndSimulationCannotBecomePaidInCommercialMode() throws Exception {
    var b = new Browser().signup();
    var s = entity(b);
    s.setState(SaasSubscription.State.LEGACY);
    s.setPlan(SaasPlan.Code.ESSENTIAL);
    subscriptions.saveAndFlush(s);
    for (int i = 0; i < 4; i++) b.account("Legado " + i);
    assertEquals("LEGACY", view(b).path("status").asText());
    assertFalse(view(b).path("limited").asBoolean());
    request(b, "ESSENTIAL", view(b).path("revision").asLong(), 409);
    s = entity(b);
    s.setState(SaasSubscription.State.SIMULATED);
    subscriptions.saveAndFlush(s);
    assertEquals("INACTIVE", view(b).path("status").asText());
    b.request(
        "POST",
        "/finance/accounts",
        Map.of("name", "Simulação não paga", "initialBalance", 0),
        402);
  }

  byte[] png() throws Exception {
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    try (var out = new java.io.ByteArrayOutputStream()) {
      javax.imageio.ImageIO.write(image, "PNG", out);
      return out.toByteArray();
    }
  }

  HttpResponse<byte[]> upload(Browser b, String entry, byte[] data) throws Exception {
    String boundary = "fintry-" + UUID.randomUUID();
    var out = new java.io.ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"comprovante.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.write(data);
    out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var req =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/api/v1/finance/entries/"
                        + entry
                        + "/attachments"))
            .header(b.header, b.token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build();
    return b.http.send(req, HttpResponse.BodyHandlers.ofByteArray());
  }

  @Test
  void storageQuotaIsSharedAcrossEntriesAndRemovalRemainsPossibleAfterExpiration()
      throws Exception {
    var b = new Browser().signup();
    active(b, SaasPlan.Code.ESSENTIAL);
    String account = b.account("Comprovantes");
    String one = entry(b, account), two = entry(b, account);
    byte[] bytes = png();
    var plan = plans.findById(SaasPlan.Code.ESSENTIAL).orElseThrow();
    long original = plan.getStorageBytes();
    plan.setStorageBytes(bytes.length * 2L);
    plans.saveAndFlush(plan);
    try {
      var first = upload(b, one, bytes);
      assertEquals(201, first.statusCode(), new String(first.body()));
      String id = json.readTree(first.body()).path("id").asText();
      var pool = Executors.newFixedThreadPool(2);
      try {
        var a = pool.submit(() -> upload(b, one, bytes).statusCode());
        var c = pool.submit(() -> upload(b, two, bytes).statusCode());
        var status =
            new ArrayList<>(List.of(a.get(30, TimeUnit.SECONDS), c.get(30, TimeUnit.SECONDS)));
        Collections.sort(status);
        assertEquals(List.of(201, 409), status);
      } finally {
        pool.shutdownNow();
      }
      assertEquals(bytes.length * 2L, view(b).path("usage").path("storageBytes").asLong());
      var sub = entity(b);
      sub.setPaidUntil(Instant.now().minusSeconds(1));
      subscriptions.saveAndFlush(sub);
      assertEquals(402, upload(b, two, bytes).statusCode());
      b.request("DELETE", "/finance/entries/" + one + "/attachments/" + id, null, 204);
      assertEquals(bytes.length, view(b).path("usage").path("storageBytes").asLong());
    } finally {
      plan = plans.findById(SaasPlan.Code.ESSENTIAL).orElseThrow();
      plan.setStorageBytes(original);
      plans.saveAndFlush(plan);
    }
  }
}
