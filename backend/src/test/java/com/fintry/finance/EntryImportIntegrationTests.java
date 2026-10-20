package com.fintry.finance;

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
    properties = {"spring.profiles.default=test", "fintry.mail.enabled=false"})
class EntryImportIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(60000000000000L);
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

  Map<String, Object> input(String account, String csv) {
    return Map.of("accountId", account, "csv", csv);
  }

  Map<String, Object> confirm(String account, String csv, Integer... rows) {
    return Map.of("accountId", account, "csv", csv, "selectedRows", List.of(rows));
  }

  JsonNode preview(Browser b, String account, String csv) throws Exception {
    return b.request("POST", "/finance/imports/preview", input(account, csv), 200);
  }

  String row(
      String description,
      String type,
      String amount,
      String category,
      String status,
      String payment) {
    return description
        + ";"
        + type
        + ";"
        + amount
        + ";"
        + today
        + ";"
        + category
        + ";"
        + status
        + ";"
        + payment
        + "\n";
  }

  int count(Browser b) throws Exception {
    return b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt();
  }

  @Test
  void previewsWithoutWritesThenImportsSelectionLinksCategoriesBalancesAndAudit() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Banco");
    var cat =
        b.request("POST", "/categories", Map.of("name", "Serviços", "kind", "INCOME"), 201)
            .get("id")
            .asText();
    var csv =
        header
            + row("Consultoria", "receita", "1.234,56", "serviços", "realizado", today)
            + row("Material", "despesa", "89.90", "Escritório", "pendente", "")
            + row("consultoria", "receita", "1234.56", "Outra categoria", "pendente", "")
            + row("Erro", "despesa", "-1", "Outros", "pendente", "");
    var p = preview(b, account, csv);
    assertEquals(2, p.get("ready").asInt());
    assertEquals(1, p.get("duplicates").asInt());
    assertEquals(1, p.get("invalid").asInt());
    assertEquals(0, count(b));
    var result = b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2, 3), 200);
    assertEquals(2, result.get("imported").asInt());
    assertEquals("1234.56", result.get("income").asText());
    var list = b.request("GET", "/finance/entries", null, 200).get("content");
    for (var e : list)
      if (e.get("description").asText().equals("Consultoria")) {
        assertEquals(cat, e.get("categoryId").asText());
        assertEquals("PAID", e.get("status").asText());
      }
    var balances = b.request("GET", "/finance/accounts", null, 200);
    assertEquals(1234.56, balances.get(0).get("balance").asDouble());
    var month = YearMonth.from(LocalDate.parse(today));
    var audit =
        b.request(
            "GET",
            "/audit?start=" + month.atDay(1) + "&end=" + month.atEndOfMonth() + "&resource=ENTRY",
            null,
            200);
    assertEquals(2, audit.get("totalElements").asInt());
  }

  @Test
  void retriesAndChangedStatusOrCategoryAreDetectedAndOtherAccountsAndTenantsAreIndependent()
      throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta A");
    var csv = header + row("Freela", "receita", "50", "Outros", "pendente", "");
    assertEquals(
        1,
        b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 200)
            .get("imported")
            .asInt());
    assertEquals(1, preview(b, account, csv).get("duplicates").asInt());
    var changed = header + row("FREELA", "receita", "50,00", "Nova", "realizado", today);
    var result = b.request("POST", "/finance/imports/confirm", confirm(account, changed, 2), 200);
    assertEquals(0, result.get("imported").asInt());
    assertEquals(1, result.get("skippedDuplicates").asInt());
    var second = b.account("Conta B");
    assertEquals(1, preview(b, second, csv).get("ready").asInt());
    var other = new Browser().signup();
    var foreign = other.account("Conta C");
    assertEquals(1, preview(other, foreign, csv).get("ready").asInt());
    assertEquals(1, count(b));
    assertEquals(0, count(other));
  }

  @Test
  void revalidatesAfterPreviewAndSerializesSimultaneousConfirmations() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var csv = header + row("Importação concorrente", "receita", "10", "Outros", "pendente", "");
    assertEquals(1, preview(b, account, csv).get("ready").asInt());
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      Callable<JsonNode> call =
          () -> {
            gate.await();
            return b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 200);
          };
      var one = pool.submit(call);
      var two = pool.submit(call);
      gate.countDown();
      var a = one.get(40, TimeUnit.SECONDS);
      var c = two.get(40, TimeUnit.SECONDS);
      assertEquals(1, a.get("imported").asInt() + c.get("imported").asInt());
      assertEquals(1, a.get("skippedDuplicates").asInt() + c.get("skippedDuplicates").asInt());
      assertEquals(1, count(b));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void invalidSelectionRejectsWholeImportAndMalformedFilesDoNotWrite() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var csv =
        header
            + row("Válido", "receita", "10", "Outros", "pendente", "")
            + row("Inválido", "receita", "0", "Outros", "pendente", "");
    for (var selection :
        List.of(
            new Integer[] {2, 3}, new Integer[] {2, 99}, new Integer[] {2, 2}, new Integer[] {}))
      b.request("POST", "/finance/imports/confirm", confirm(account, csv, selection), 400);
    b.request("POST", "/finance/imports/preview", input(account, "descricao;tipo\na;b"), 400);
    b.request("POST", "/finance/imports/confirm", confirm(account, csv, 3), 400);
    assertEquals(0, count(b));
    assertEquals(
        1,
        b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 200)
            .get("imported")
            .asInt());
  }

  @Test
  void rowErrorsCoverPaymentDatesAndInactiveOrIncompatibleCategories() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var cat =
        b.request("POST", "/categories", Map.of("name", "Inativa", "kind", "EXPENSE"), 201)
            .get("id")
            .asText();
    b.request(
        "PUT",
        "/categories/" + cat,
        Map.of("name", "Inativa", "kind", "EXPENSE", "active", false),
        200);
    b.request("POST", "/categories", Map.of("name", "Só despesa", "kind", "EXPENSE"), 201);
    var csv =
        header
            + row("Data ausente", "receita", "10", "Outros", "realizado", "")
            + row(
                "Data futura",
                "receita",
                "10",
                "Outros",
                "realizado",
                LocalDate.parse(today).plusDays(1).toString())
            + row("Pendente com pagamento", "receita", "10", "Outros", "pendente", today)
            + row("Inativa", "despesa", "10", "Inativa", "pendente", "")
            + row("Tipo da categoria", "receita", "10", "Só despesa", "pendente", "")
            + row("Tipo inválido", "transferencia", "10", "Outros", "pendente", "")
            + row("Estado inválido", "receita", "10", "Outros", "cancelado", "");
    var p = preview(b, account, csv);
    assertEquals(7, p.get("invalid").asInt());
    assertEquals(0, p.get("ready").asInt());
    assertEquals(0, count(b));
  }

  @Test
  void cancelledEntriesDoNotBlockImportButRepeatedRowsInFileAreSkipped() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var csv = header + row("Reimportável", "receita", "10", "Outros", "pendente", "");
    b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 200);
    var id =
        b.request("GET", "/finance/entries", null, 200).get("content").get(0).get("id").asText();
    b.request("POST", "/finance/entries/" + id + "/cancel", null, 200);
    csv += row("reimportável", "receita", "10", "Nova", "realizado", today);
    var p = preview(b, account, csv);
    assertEquals(1, p.get("ready").asInt());
    assertEquals(1, p.get("duplicates").asInt());
    var r = b.request("POST", "/finance/imports/confirm", confirm(account, csv, 2, 3), 200);
    assertEquals(1, r.get("imported").asInt());
    assertEquals(1, r.get("skippedDuplicates").asInt());
  }

  @Test
  void preservesCentsInTotalsBeyondJavaScriptSafeIntegerRange() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Valores exatos");
    var csv = new StringBuilder(header);
    var selection = new ArrayList<Integer>();
    for (int i = 0; i < 91; i++) {
      csv.append(row("Contrato " + i, "receita", "999999999999,99", "Outros", "pendente", ""));
      selection.add(i + 2);
    }
    var result =
        b.request(
            "POST",
            "/finance/imports/confirm",
            Map.of("accountId", account, "csv", csv.toString(), "selectedRows", selection),
            200);
    assertEquals(91, result.get("imported").asInt());
    assertEquals("90999999999999.09", result.get("income").asText());
    assertTrue(result.get("income").isString());
  }

  @Test
  void enforcesCompanyRoleAndCsrfAndTemplateIsDownloadable() throws Exception {
    var b = new Browser().signup();
    var other = new Browser().signup();
    var account = b.account("Conta");
    var csv = header + row("Seguro", "receita", "10", "Outros", "pendente", "");
    other.request("POST", "/finance/imports/preview", input(account, csv), 404);
    other.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 404);
    var token = b.token;
    b.token = null;
    b.request("POST", "/finance/imports/preview", input(account, csv), 403);
    b.token = token;
    var viewerEmail = UUID.randomUUID() + "@example.com";
    var invite =
        b.request(
            "POST",
            "/users",
            Map.of("name", "Consulta", "email", viewerEmail, "role", "VIEWER"),
            201);
    var viewer = new Browser();
    viewer.email = viewerEmail;
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
    viewer.login();
    viewer.request("POST", "/finance/imports/preview", input(account, csv), 403);
    viewer.request("POST", "/finance/imports/confirm", confirm(account, csv, 2), 403);
    var download =
        b.http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1/finance/imports/template"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, download.statusCode());
    assertTrue(
        download.headers().firstValue("content-disposition").orElse("").contains("attachment"));
    assertEquals(2, CsvEntryParser.parse(download.body()).size());
    assertEquals(0, count(b));
  }
}
