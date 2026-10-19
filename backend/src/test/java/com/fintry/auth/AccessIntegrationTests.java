package com.fintry.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP sessions, CSRF, migrations and transactions. Only outbound email is captured. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.default=test",
      "fintry.mail.enabled=true",
      "fintry.base-url=https://fintry.example"
    })
class AccessIntegrationTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired CaptureMail mail;

  static class CaptureMail extends JavaMailSenderImpl {
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();

    @Override
    public void send(SimpleMailMessage message) {
      messages.add(new SimpleMailMessage(message));
    }
  }

  @TestConfiguration
  static class MailConfig {
    @Bean
    @Primary
    CaptureMail captureMail() {
      return new CaptureMail();
    }
  }

  static AtomicLong documents = new AtomicLong(20000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
  final String password = "Senha-segura-123";
  BlockingQueue<SimpleMailMessage> emails;

  @BeforeEach
  void capture() {
    emails = mail.messages;
    emails.clear();
  }

  class Browser {
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String token, header, email = UUID.randomUUID() + "@example.com", id;

    HttpResponse<String> raw(String method, String path, Object body, boolean csrf)
        throws Exception {
      var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
      if (csrf && token != null) r.header(header, token);
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

    JsonNode request(String method, String path, Object body, int expected) throws Exception {
      var r = raw(method, path, body, true);
      assertEquals(expected, r.statusCode(), r.body());
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
              "Responsável",
              "companyName",
              "Empresa acesso",
              "document",
              "" + documents.incrementAndGet(),
              "email",
              email,
              "password",
              password),
          201);
      login(password);
      return this;
    }

    void login(String pass) throws Exception {
      csrf();
      var r = request("POST", "/auth/login", Map.of("email", email, "password", pass), 200);
      id = r.get("id").asText();
      csrf();
    }

    JsonNode invite(String role, String email) throws Exception {
      return request(
          "POST", "/users", Map.of("name", "Pessoa convidada", "email", email, "role", role), 201);
    }

    String audit(String resource) throws Exception {
      return raw(
              "GET",
              "/audit?start=" + today + "&end=" + today + "&resource=" + resource,
              null,
              true)
          .body();
    }
  }

  String token(JsonNode invite) {
    String link = invite.get("link").asText();
    assertTrue(link.startsWith("https://fintry.example/#invite="));
    return link.substring(link.indexOf('=') + 1);
  }

  String memberId(Browser admin, String email) throws Exception {
    return admin
        .request(
            "GET",
            "/users?search=" + URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8),
            null,
            200)
        .get("content")
        .get(0)
        .get("id")
        .asText();
  }

  Browser accepted(Browser admin, String role) throws Exception {
    var b = new Browser();
    var t = token(admin.invite(role, b.email));
    b.csrf();
    b.request(
        "POST",
        "/auth/accept-invite",
        Map.of("token", t, "name", "Pessoa equipe", "password", password),
        204);
    b.login(password);
    return b;
  }

  Map<String, Object> edit(String role, boolean active) {
    return Map.of("name", "Pessoa equipe", "role", role, "active", active);
  }

  @Test
  void rolesEnforceApiPermissionsAndRevocation() throws Exception {
    var admin = new Browser().signup();
    var viewer = accepted(admin, "VIEWER");
    var finance = accepted(admin, "FINANCE");
    assertEquals("VIEWER", viewer.request("GET", "/auth/me", null, 200).get("role").asText());
    viewer.request("GET", "/finance/accounts", null, 200);
    viewer.request("GET", "/reports?start=" + today + "&end=" + today, null, 200);
    viewer.request(
        "POST", "/finance/accounts", Map.of("name", "Proibida", "initialBalance", "0"), 403);
    viewer.request("PUT", "/contacts/" + UUID.randomUUID(), Map.of(), 403);
    viewer.request(
        "DELETE",
        "/finance/entries/" + UUID.randomUUID() + "/attachments/" + UUID.randomUUID(),
        null,
        403);
    viewer.request("GET", "/users", null, 403);
    viewer.request("GET", "/audit?start=" + today + "&end=" + today, null, 403);
    finance.request(
        "POST", "/finance/accounts", Map.of("name", "Operação", "initialBalance", "0"), 201);
    finance.request("GET", "/users", null, 403);
    finance.request(
        "PUT",
        "/companies/" + admin.request("GET", "/auth/me", null, 200).get("companyId").asText(),
        Map.of(),
        403);
    admin.request("PUT", "/users/" + finance.id, edit("VIEWER", true), 204);
    finance.request("GET", "/auth/me", null, 401);
    finance.login(password);
    finance.request(
        "POST", "/finance/accounts", Map.of("name", "Não pode", "initialBalance", "0"), 403);
    admin.request("PUT", "/users/" + viewer.id, edit("VIEWER", false), 204);
    viewer.request("GET", "/finance/accounts", null, 401);
    viewer.csrf();
    viewer.request("POST", "/auth/login", Map.of("email", viewer.email, "password", password), 401);
    admin.request("PUT", "/users/" + admin.id, edit("VIEWER", true), 400);
    admin.request("PUT", "/users/" + admin.id, edit("ADMIN", false), 400);
  }

  @Test
  void invitationsAreScopedHashedRenewableRevocableAndSingleUse() throws Exception {
    var admin = new Browser().signup();
    var other = new Browser().signup();
    var person = new Browser();
    var invite = admin.invite("FINANCE", person.email);
    String first = token(invite), id = memberId(admin, person.email);
    assertEquals(
        AccessFlows.hash(first),
        jdbc.queryForObject(
            "select token_hash from access_tokens where user_id=? and used_at is null",
            String.class,
            UUID.fromString(id)));
    other.request("PUT", "/users/" + id, edit("ADMIN", true), 404);
    other.request("POST", "/users/" + id + "/invitation", null, 404);
    other.request("DELETE", "/users/" + id + "/invitation", null, 404);
    person.csrf();
    assertEquals(
        403,
        person
            .raw(
                "POST",
                "/auth/accept-invite",
                Map.of("token", first, "name", "Sem CSRF", "password", password),
                false)
            .statusCode());
    person.request("POST", "/auth/login", Map.of("email", person.email, "password", password), 401);
    person.request("POST", "/auth/invitation", Map.of("token", first), 200);
    String second = token(admin.request("POST", "/users/" + id + "/invitation", null, 200));
    person.request("POST", "/auth/invitation", Map.of("token", first), 400);
    admin.request("DELETE", "/users/" + id + "/invitation", null, 204);
    person.request("POST", "/auth/invitation", Map.of("token", second), 400);
    String third = token(admin.request("POST", "/users/" + id + "/invitation", null, 200));
    jdbc.update(
        "update access_tokens set expires_at=? where token_hash=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        AccessFlows.hash(third));
    person.request("POST", "/auth/invitation", Map.of("token", third), 400);
    String finalToken = token(admin.request("POST", "/users/" + id + "/invitation", null, 200));
    var competitor = new Browser();
    competitor.csrf();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var tasks =
          List.<Callable<Integer>>of(
              () ->
                  person
                      .raw(
                          "POST",
                          "/auth/accept-invite",
                          Map.of("token", finalToken, "name", "Aceito", "password", password),
                          true)
                      .statusCode(),
              () ->
                  competitor
                      .raw(
                          "POST",
                          "/auth/accept-invite",
                          Map.of("token", finalToken, "name", "Aceito", "password", password),
                          true)
                      .statusCode());
      var statuses = new ArrayList<Integer>();
      for (var result : pool.invokeAll(tasks)) statuses.add(result.get());
      Collections.sort(statuses);
      assertEquals(List.of(204, 400), statuses);
    } finally {
      pool.shutdownNow();
    }
    person.login(password);
    assertEquals("FINANCE", person.request("GET", "/auth/me", null, 200).get("role").asText());
    assertFalse(admin.audit("USER").contains(finalToken));
    assertFalse(other.audit("USER").contains(id));
    admin.request(
        "POST",
        "/users",
        Map.of(
            "name", "Duplicado", "email", person.email.toUpperCase(Locale.ROOT), "role", "VIEWER"),
        409);
  }

  @Test
  void passwordRecoveryDeliversOnlyToKnownActiveAccountsAndInvalidatesSessions() throws Exception {
    var user = new Browser().signup();
    var anon = new Browser();
    anon.csrf();
    var known = anon.raw("POST", "/auth/forgot-password", Map.of("email", user.email), true);
    var unknown =
        anon.raw(
            "POST",
            "/auth/forgot-password",
            Map.of("email", UUID.randomUUID() + "@example.com"),
            true);
    assertEquals(202, known.statusCode());
    assertEquals(known.statusCode(), unknown.statusCode());
    assertEquals(known.body(), unknown.body());
    var email = emails.poll(10, TimeUnit.SECONDS);
    assertNotNull(email);
    assertArrayEquals(new String[] {user.email}, email.getTo());
    String link =
        Arrays.stream(email.getText().split("\\s+"))
            .filter(x -> x.startsWith("https://fintry.example/#reset="))
            .findFirst()
            .orElseThrow();
    String raw = link.substring(link.indexOf('=') + 1);
    assertFalse(known.body().contains(raw));
    assertFalse(user.audit("ACCESS").contains(raw));
    assertEquals(
        AccessFlows.hash(raw),
        jdbc.queryForObject(
            "select token_hash from access_tokens where user_id=? and kind='RESET' and used_at is"
                + " null",
            String.class,
            UUID.fromString(user.id)));
    anon.request(
        "POST",
        "/auth/accept-invite",
        Map.of("token", raw, "name", "Inválido", "password", password),
        400);
    anon.request("POST", "/auth/reset-password", Map.of("token", raw, "password", "123"), 400);
    anon.request(
        "POST",
        "/auth/reset-password",
        Map.of("token", raw, "password", "Nova-senha-segura-789"),
        204);
    anon.csrf();
    anon.request("POST", "/auth/reset-password", Map.of("token", raw, "password", password), 400);
    user.request("GET", "/auth/me", null, 401);
    user.csrf();
    user.request("POST", "/auth/login", Map.of("email", user.email, "password", password), 401);
    user.login("Nova-senha-segura-789");
    anon.request("POST", "/auth/forgot-password", Map.of("email", user.email), 202);
    var next = emails.poll(10, TimeUnit.SECONDS);
    assertNotNull(next);
    String expired = next.getText().split("#reset=")[1].split("\\s+")[0];
    jdbc.update(
        "update access_tokens set expires_at=? where token_hash=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        AccessFlows.hash(expired));
    anon.request(
        "POST", "/auth/reset-password", Map.of("token", expired, "password", password), 400);
    anon.request("POST", "/auth/forgot-password", Map.of("email", user.email), 202);
    anon.request("POST", "/auth/forgot-password", Map.of("email", user.email), 429);
  }

  @Test
  void ownPasswordChangeRejectsWrongPasswordAndRevokesAllSessions() throws Exception {
    var admin = new Browser().signup();
    var person = accepted(admin, "VIEWER");
    var another = new Browser();
    another.email = person.email;
    another.login(password);
    person.request(
        "POST",
        "/auth/change-password",
        Map.of("currentPassword", "errada", "password", "Nova-senha-segura-789"),
        400);
    person.request(
        "POST",
        "/auth/change-password",
        Map.of("currentPassword", password, "password", password),
        400);
    person.request(
        "POST",
        "/auth/change-password",
        Map.of("currentPassword", password, "password", "Nova-senha-segura-789"),
        204);
    another.request("GET", "/auth/me", null, 401);
    person.login("Nova-senha-segura-789");
    assertTrue(admin.audit("ACCESS").contains("PASSWORD_CHANGE"));
    assertFalse(admin.audit("ACCESS").contains("Nova-senha"));
  }

  @Test
  void auditCapturesFieldDifferencesNoOpsRollbackAndTenantIsolation() throws Exception {
    var admin = new Browser().signup();
    var other = new Browser().signup();
    String account =
        admin
            .request(
                "POST",
                "/finance/accounts",
                Map.of("name", "Conta auditável", "initialBalance", "0"),
                201)
            .get("id")
            .asText();
    var input =
        new HashMap<String, Object>(
            Map.of(
                "accountId",
                account,
                "description",
                "Fatura auditável",
                "category",
                "Serviços",
                "type",
                "INCOME",
                "amount",
                "100.00",
                "dueDate",
                today,
                "paid",
                false));
    String id = admin.request("POST", "/finance/entries", input, 201).get("id").asText();
    var original =
        admin.request(
            "GET", "/audit?start=" + today + "&end=" + today + "&resource=ENTRY", null, 200);
    assertEquals(1, original.get("totalElements").asInt());
    admin.request("PUT", "/finance/entries/" + id, input, 200);
    assertEquals(
        1,
        admin
            .request(
                "GET", "/audit?start=" + today + "&end=" + today + "&resource=ENTRY", null, 200)
            .get("totalElements")
            .asInt());
    input.put("amount", "125.50");
    admin.request("PUT", "/finance/entries/" + id, input, 200);
    var updated =
        admin
            .request(
                "GET", "/audit?start=" + today + "&end=" + today + "&resource=ENTRY", null, 200)
            .get("content")
            .get(0);
    assertEquals("UPDATE", updated.get("action").asText());
    assertEquals("Valor", updated.get("changes").get(0).get("field").asText());
    assertEquals("125.5", updated.get("changes").get(0).get("after").asText());
    admin.request("POST", "/finance/entries/" + id + "/pay", Map.of("paymentDate", today), 200);
    admin.request("POST", "/finance/entries/" + id + "/pay", Map.of("paymentDate", today), 200);
    assertEquals(
        3,
        admin
            .request(
                "GET", "/audit?start=" + today + "&end=" + today + "&resource=ENTRY", null, 200)
            .get("totalElements")
            .asInt());
    input.put("accountId", UUID.randomUUID());
    admin.request("POST", "/finance/entries", input, 404);
    assertEquals(
        3,
        admin
            .request(
                "GET", "/audit?start=" + today + "&end=" + today + "&resource=ENTRY", null, 200)
            .get("totalElements")
            .asInt());
    assertFalse(other.audit("ENTRY").contains(id));
    admin.request("GET", "/audit?start=2020-01-01&end=" + today, null, 400);
    admin.request("GET", "/users?page=-1", null, 400);
    assertFalse(admin.audit("ENTRY").contains("passwordHash"));
  }
}
