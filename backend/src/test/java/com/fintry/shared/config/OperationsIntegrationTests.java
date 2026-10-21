package com.fintry.shared.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.default=test",
      "fintry.mail.enabled=true",
      "spring.mail.host=mailpit"
    })
class OperationsIntegrationTests {
  @org.springframework.beans.factory.annotation.Autowired CaptureMail mail;

  static class CaptureMail extends org.springframework.mail.javamail.JavaMailSenderImpl {
    final java.util.concurrent.BlockingQueue<org.springframework.mail.SimpleMailMessage> messages =
        new java.util.concurrent.LinkedBlockingQueue<>();
    boolean fail;

    @Override
    public void send(org.springframework.mail.SimpleMailMessage message) {
      if (fail)
        throw new org.springframework.mail.MailSendException(
            "fixture secret should never be returned");
      messages.add(new org.springframework.mail.SimpleMailMessage(message));
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class MailConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    CaptureMail captured() {
      return new CaptureMail();
    }
  }

  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(90000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
  final String header = "descricao;tipo;valor;vencimento;categoria;situacao;data_pagamento\n";
  final String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

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

  Browser member(Browser admin, String role) throws Exception {
    var person = new Browser();
    var invite =
        admin.request(
            "POST", "/users", Map.of("name", "Pessoa", "email", person.email, "role", role), 201);
    person.csrf();
    person.request(
        "POST",
        "/auth/accept-invite",
        Map.of(
            "token",
            invite.get("link").asText().split("=")[1],
            "password",
            password,
            "name",
            "Pessoa"),
        204);
    person.login();
    return person;
  }

  @Test
  void adminSeesSafeDiagnosticsAndAnonymousProbesContainOnlyStatus() throws Exception {
    var b = new Browser().signup();
    var status = b.request("GET", "/operations", null, 200);
    assertEquals("UP", status.get("database").asText());
    assertTrue(status.get("ready").asBoolean());
    assertEquals("LOCAL", status.get("mailMode").asText());
    assertFalse(status.has("password"));
    assertFalse(status.has("host"));
    assertFalse(status.has("databaseUrl"));
    assertFalse(status.get("production").asBoolean());
    var anon = new Browser();
    anon.request("GET", "/operations", null, 401);
    for (var path : List.of("liveness", "readiness")) {
      var response =
          anon.http.send(
              HttpRequest.newBuilder(
                      URI.create("http://localhost:" + port + "/actuator/health/" + path))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      var data = json.readTree(response.body());
      assertEquals(1, data.size());
      assertEquals("UP", data.get("status").asText());
    }
    for (var role : List.of("VIEWER", "FINANCE")) {
      var user = member(b, role);
      user.request("GET", "/operations", null, 403);
      user.request("POST", "/operations/mail-test", Map.of(), 403);
    }
  }

  @Test
  void testMailUsesOnlyAuthenticatedAdminRecipientAndEnforcesCsrfAndLimit() throws Exception {
    mail.fail = false;
    mail.messages.clear();
    var b = new Browser().signup();
    var token = b.token;
    b.token = null;
    b.request("POST", "/operations/mail-test", Map.of(), 403);
    b.token = token;
    for (int i = 0; i < 3; i++)
      b.request("POST", "/operations/mail-test", Map.of("recipient", "other@example.test"), 202);
    b.request("POST", "/operations/mail-test", Map.of(), 429);
    assertEquals(3, mail.messages.size());
    for (var message : mail.messages) {
      assertArrayEquals(new String[] {b.email}, message.getTo());
      assertTrue(message.getSubject().contains("Teste"));
    }
    String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
    assertEquals(
        3,
        b.request(
                "GET",
                "/audit?start=" + today + "&end=" + today + "&resource=OPERATIONS",
                null,
                200)
            .get("totalElements")
            .asInt());
  }

  @Test
  void smtpFailureIsGenericAndDoesNotClaimDelivery() throws Exception {
    var b = new Browser().signup();
    mail.fail = true;
    try {
      var response = b.request("POST", "/operations/mail-test", Map.of(), 503);
      assertFalse(response.toString().contains("fixture secret"));
      assertTrue(response.toString().contains("SMTP"));
    } finally {
      mail.fail = false;
    }
  }
}
