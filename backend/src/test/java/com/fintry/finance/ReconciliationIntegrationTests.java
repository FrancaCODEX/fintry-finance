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
class ReconciliationIntegrationTests {
  @LocalServerPort int port;
  static final AtomicLong DOCS = new AtomicLong(80000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
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

  final String base = "/finance/reconciliation";

  String ofx(String... rows) {
    return "OFXHEADER:100\n"
               + "DATA:OFXSGML\n"
               + "VERSION:102\n\n"
               + "<OFX><BANKMSGSRSV1><STMTTRNRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>001<BRANCHID>1234<ACCTID>998877<ACCTTYPE>CHECKING</BANKACCTFROM><BANKTRANLIST>"
        + String.join("", rows)
        + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
  }

  String txn(String id, String amount, String name) {
    return "<STMTTRN><TRNTYPE>OTHER<DTPOSTED>"
        + today.replace("-", "")
        + "120000.000[-3:BRT]<TRNAMT>"
        + amount
        + "<FITID>"
        + id
        + "<NAME>"
        + name
        + "</STMTTRN>";
  }

  Map<String, Object> file(String account, String ofx) {
    return Map.of("accountId", account, "ofx", ofx);
  }

  JsonNode preview(Browser b, String account, String ofx) throws Exception {
    return b.request("POST", base + "/preview", file(account, ofx), 200);
  }

  JsonNode upload(Browser b, String account, String ofx, Integer... selected) throws Exception {
    return b.request(
        "POST",
        base + "/import",
        Map.of("accountId", account, "ofx", ofx, "selectedRows", List.of(selected)),
        200);
  }

  JsonNode list(Browser b, String account) throws Exception {
    return b.request("GET", base + "?accountId=" + account, null, 200);
  }

  JsonNode decision(Browser b, JsonNode row, String action, Map<String, Object> extra, int status)
      throws Exception {
    var input = new HashMap<String, Object>(extra);
    input.put("action", action);
    input.put("revision", row.get("revision").asLong());
    return b.request("POST", base + "/" + row.get("id").asText() + "/resolve", input, status);
  }

  JsonNode entry(Browser b, String account, String amount, String description, boolean paid)
      throws Exception {
    var input =
        new HashMap<String, Object>(
            Map.of(
                "accountId",
                account,
                "amount",
                amount,
                "description",
                description,
                "category",
                "Outros",
                "type",
                "INCOME",
                "dueDate",
                today,
                "paid",
                paid));
    if (paid) input.put("paymentDate", today);
    return b.request("POST", "/finance/entries", input, 201);
  }

  double balance(Browser b) throws Exception {
    return b.request("GET", "/finance/accounts", null, 200).get(0).get("balance").asDouble();
  }

  JsonNode byFit(JsonNode list, String fit) {
    for (var r : list.get("content")) if (r.get("fitId").asText().equals(fit)) return r;
    throw new AssertionError(fit);
  }

  @Test
  void previewsImportsWithoutChangingMoneyAndReimportsSafely() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var text =
        ofx(
            txn("001", "123.45", "Cliente &amp; Cia"),
            txn("002", "-12.30", "Tarifa"),
            txn("001", "123.45", "Cliente &amp; Cia"),
            txn("bad", "0", "Zero"));
    var p = preview(b, a, text);
    assertEquals(2, p.get("ready").asInt());
    assertEquals(1, p.get("duplicates").asInt());
    assertEquals(1, p.get("invalid").asInt());
    assertEquals(0, list(b, a).get("totalElements").asInt());
    var r = upload(b, a, text, 1, 2, 3);
    assertEquals(2, r.get("imported").asInt());
    assertEquals(1, r.get("skipped").asInt());
    assertEquals(0, balance(b));
    assertEquals(2, upload(b, a, text, 1, 2).get("skipped").asInt());
    assertEquals(2, list(b, a).get("open").asInt());
    assertEquals("Cliente & Cia", byFit(list(b, a), "001").get("description").asText());
    p = preview(b, a, ofx(txn("001", "999.00", "Alterado")));
    assertEquals(1, p.get("invalid").asInt());
    b.request(
        "POST",
        base + "/import",
        Map.of("accountId", a, "ofx", text, "selectedRows", List.of(1, 4)),
        400);
    assertEquals(2, list(b, a).get("totalElements").asInt());
    var otherAccount = b.account("Outra");
    b.request("POST", base + "/preview", file(otherAccount, text), 409);
  }

  @Test
  void linksPaidWithoutChangingBalanceAndPendingWithOnePaymentThenReopensWithoutReversing()
      throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var paid = entry(b, a, "100", "Cliente A", true);
    var pending = entry(b, a, "200", "Cliente B", false);
    upload(b, a, ofx(txn("a", "100.00", "Cliente A"), txn("b", "200.00", "Cliente B")), 1, 2);
    assertEquals(100, balance(b));
    var rows = list(b, a);
    var ra = byFit(rows, "a");
    assertEquals(1, ra.get("suggestions").size());
    var linked = decision(b, ra, "LINK", Map.of("entryId", paid.get("id").asText()), 200);
    assertEquals(100, balance(b));
    decision(b, ra, "LINK", Map.of("entryId", paid.get("id").asText()), 409);
    assertEquals(100, balance(b));
    var rb = byFit(rows, "b");
    decision(b, rb, "LINK", Map.of("entryId", pending.get("id").asText()), 200);
    assertEquals(300, balance(b));
    b.request("POST", "/finance/entries/" + paid.get("id").asText() + "/cancel", null, 400);
    var reopened = decision(b, linked, "REOPEN", Map.of("reason", "Vínculo incorreto"), 200);
    assertEquals("OPEN", reopened.get("status").asText());
    assertEquals(300, balance(b));
    b.request("POST", "/finance/entries/" + paid.get("id").asText() + "/cancel", null, 200);
    assertEquals(200, balance(b));
    assertEquals(
        2,
        preview(b, a, ofx(txn("a", "100.00", "Cliente A"), txn("b", "200.00", "Cliente B")))
            .get("duplicates")
            .asInt());
  }

  @Test
  void createsExactExpenseLinksCategoryIgnoresAndRequiresReasonForEqualValues() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var cat =
        b.request("POST", "/categories", Map.of("name", "Tarifas", "kind", "EXPENSE"), 201)
            .get("id")
            .asText();
    entry(b, a, "30", "Outra receita", false);
    upload(
        b,
        a,
        ofx(
            txn("fee", "-12.34", "Tarifa"),
            txn("same", "30", "Receita distinta"),
            txn("ignore", "-7", "Revisar fora")),
        1,
        2,
        3);
    var rows = list(b, a);
    var r = decision(b, byFit(rows, "fee"), "CREATE", Map.of("categoryId", cat), 200);
    assertEquals(-12.34, balance(b));
    assertEquals(cat, r.get("entry").get("categoryId").asText());
    decision(b, byFit(rows, "same"), "CREATE", Map.of("category", "Outros"), 409);
    assertEquals(-12.34, balance(b));
    decision(
        b,
        byFit(rows, "same"),
        "CREATE",
        Map.of("category", "Outros", "reason", "Outro cliente com o mesmo valor"),
        200);
    assertEquals(17.66, balance(b));
    var ignored =
        decision(
            b,
            byFit(rows, "ignore"),
            "IGNORE",
            Map.of("reason", "Movimento já contemplado em ajuste"),
            200);
    assertEquals("IGNORED", ignored.get("status").asText());
    decision(b, ignored, "REOPEN", Map.of("reason", "Nova conferência"), 200);
    assertEquals(1, list(b, a).get("open").asInt());
    var audit =
        b.request(
            "GET",
            "/audit?start=" + today + "&end=" + today + "&resource=RECONCILIATION",
            null,
            200);
    assertTrue(audit.get("totalElements").asInt() >= 7);
  }

  @Test
  void rejectsWrongAccountTypeValueDateAndEntryAlreadyLinked() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var another = b.account("Outra");
    var e = entry(b, a, "10", "Entrada", true);
    var wrong = entry(b, another, "10", "Outra conta", true);
    var wrongValue = entry(b, a, "11", "Outro valor", true);
    var older = entry(b, a, "10", "Data diferente", false);
    b.request(
        "POST",
        "/finance/entries/" + older.get("id").asText() + "/pay",
        Map.of("paymentDate", LocalDate.parse(today).minusDays(1).toString()),
        200);
    upload(
        b,
        a,
        ofx(txn("1", "10", "Entrada"), txn("2", "10", "Segunda entrada"), txn("3", "-10", "Saída")),
        1,
        2,
        3);
    var rows = list(b, a);
    var one = byFit(rows, "1");
    for (var invalid : List.of(wrong, wrongValue, older))
      decision(b, one, "LINK", Map.of("entryId", invalid.get("id").asText()), 409);
    decision(b, byFit(rows, "3"), "LINK", Map.of("entryId", e.get("id").asText()), 409);
    decision(b, one, "LINK", Map.of("entryId", e.get("id").asText()), 200);
    decision(b, byFit(rows, "2"), "LINK", Map.of("entryId", e.get("id").asText()), 409);
    assertEquals(31, balance(b));
  }

  @Test
  void concurrentResolutionsAndImportsCannotDuplicate() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var text = ofx(txn("race", "15.15", "Concorrente"));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var jobs =
          List.of(
              pool.submit(() -> upload(b, a, text, 1)), pool.submit(() -> upload(b, a, text, 1)));
      int added = 0;
      for (var job : jobs) added += job.get(30, TimeUnit.SECONDS).get("imported").asInt();
      assertEquals(1, added);
      var row = byFit(list(b, a), "race");
      var tasks = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () -> {
                  try {
                    decision(b, row, "CREATE", Map.of("category", "Outros"), 200);
                    return 1;
                  } catch (AssertionError ex) {
                    assertTrue(ex.getMessage().contains("409"), ex.getMessage());
                    return 0;
                  }
                }));
      int success = 0;
      for (var task : tasks) success += task.get(30, TimeUnit.SECONDS);
      assertEquals(1, success);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(15.15, balance(b));
    assertEquals(1, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
  }

  @Test
  void isolatesCompaniesEnforcesRoleAndCsrfAndAllowsReadOnlyReview() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var text = ofx(txn("secure", "10", "Seguro"));
    upload(b, a, text, 1);
    var row = byFit(list(b, a), "secure");
    var other = new Browser().signup();
    other.request("POST", base + "/preview", file(a, text), 404);
    other.request("GET", base + "?accountId=" + a, null, 404);
    decision(other, row, "IGNORE", Map.of("reason", "Invasão"), 404);
    other.request("GET", base + "/" + row.get("id").asText() + "/candidates", null, 404);
    var token = b.token;
    b.token = null;
    b.request("POST", base + "/preview", file(a, text), 403);
    b.token = token;
    var email = UUID.randomUUID() + "@example.com";
    var invite =
        b.request(
            "POST", "/users", Map.of("name", "Consulta", "email", email, "role", "VIEWER"), 201);
    var viewer = new Browser();
    viewer.email = email;
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
    assertEquals(1, list(viewer, a).get("open").asInt());
    viewer.request("POST", base + "/preview", file(a, text), 403);
    decision(viewer, row, "IGNORE", Map.of("reason", "Não permitido"), 403);
  }

  @Test
  void protectsReconciledSalesAndMatchesTransfersWithoutCreatingMoreEntries() throws Exception {
    var b = new Browser().signup();
    var a = b.account("Banco");
    var destination = b.account("Destino");
    b.request(
        "POST",
        "/finance/transfers",
        Map.of(
            "sourceId",
            a,
            "destinationId",
            destination,
            "amount",
            "10",
            "date",
            today,
            "description",
            "Transferência"),
        201);
    var physical =
        b.request(
                "POST",
                "/products",
                Map.of(
                    "sku",
                    "TEST",
                    "name",
                    "Produto",
                    "unit",
                    "UN",
                    "price",
                    "50",
                    "cost",
                    "5",
                    "initialQuantity",
                    "4",
                    "minimum",
                    "0",
                    "active",
                    true),
                201)
            .get("id")
            .asText();
    var sale =
        b.request(
            "POST",
            "/sales",
            Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "items",
                List.of(Map.of("productId", physical, "quantity", "1", "unitPrice", "50"))),
            201);
    var confirmed =
        b.request(
            "POST",
            "/sales/" + sale.get("id").asText() + "/confirm",
            Map.of("accountId", a, "dueDate", today, "paid", true, "paymentDate", today),
            200);
    upload(b, a, ofx(txn("sale", "50", "Venda"), txn("transfer", "-10", "Transferência")), 1, 2);
    var rows = list(b, a);
    var linked =
        decision(
            b,
            byFit(rows, "sale"),
            "LINK",
            Map.of("entryId", confirmed.get("entryId").asText()),
            200);
    b.request("POST", "/sales/" + sale.get("id").asText() + "/cancel", null, 400);
    var candidates =
        b.request(
            "GET",
            base + "/" + byFit(rows, "transfer").get("id").asText() + "/candidates",
            null,
            200);
    assertEquals(1, candidates.size());
    assertFalse(candidates.get(0).get("entry").get("transferId").isNull());
    decision(
        b,
        byFit(rows, "transfer"),
        "LINK",
        Map.of("entryId", candidates.get(0).get("entry").get("id").asText()),
        200);
    assertEquals(40, balance(b));
    assertEquals(3, b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt());
    decision(b, linked, "REOPEN", Map.of("reason", "Corrigir venda"), 200);
    b.request("POST", "/sales/" + sale.get("id").asText() + "/cancel", null, 200);
    assertEquals(-10, balance(b));
    var products = b.request("GET", "/products", null, 200).get("content");
    assertEquals(4, products.get(0).get("quantity").asInt());
  }

  @Test
  void upgradesExistingFinancialDataWithoutChangingBalancesOrLinks() throws Exception {
    try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
      pool.setJdbcUrl(
          "jdbc:h2:mem:reconcile-upgrade-"
              + UUID.randomUUID()
              + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
      pool.setUsername("sa");
      pool.setPassword("");
      org.flywaydb.core.Flyway.configure()
          .dataSource(pool)
          .locations("classpath:db/demo")
          .target("9")
          .load()
          .migrate();
      var company = UUID.randomUUID();
      var account = UUID.randomUUID();
      var entry = UUID.randomUUID();
      try (var c = pool.getConnection();
          var st = c.createStatement()) {
        st.executeUpdate(
            "insert into companies(id,legal_name,document,created_at,updated_at) values('"
                + company
                + "','Legado','98765432000100',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        st.executeUpdate(
            "insert into"
                + " financial_accounts(id,company_id,name,initial_balance,created_at,updated_at)"
                + " values('"
                + account
                + "','"
                + company
                + "','Banco',123.45,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        st.executeUpdate(
            "insert into"
                + " financial_entries(id,company_id,account_id,description,category,type,amount,due_date,payment_date,status,created_at,updated_at)"
                + " values('"
                + entry
                + "','"
                + company
                + "','"
                + account
                + "','Anterior','Outros','INCOME',12.34,CURRENT_DATE,CURRENT_DATE,'PAID',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      }
      org.flywaydb.core.Flyway.configure()
          .dataSource(pool)
          .locations("classpath:db/demo")
          .load()
          .migrate();
      try (var c = pool.getConnection();
          var st = c.createStatement();
          var r =
              st.executeQuery(
                  "select a.initial_balance+e.amount,e.description,e.status from financial_accounts"
                      + " a join financial_entries e on e.account_id=a.id")) {
        assertTrue(r.next());
        assertEquals("135.79", r.getBigDecimal(1).toPlainString());
        assertEquals("Anterior", r.getString(2));
        assertEquals("PAID", r.getString(3));
      }
      try (var c = pool.getConnection();
          var st = c.createStatement();
          var r = st.executeQuery("select count(*) from statement_lines")) {
        assertTrue(r.next());
        assertEquals(0, r.getInt(1));
      }
    }
  }
}
