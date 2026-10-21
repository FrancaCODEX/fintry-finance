package com.fintry.commerce;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.default=test", "fintry.mail.enabled=false"})
class ProposalIntegrationTests {
  @LocalServerPort int port;
  @Autowired ProposalRepository proposals;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  static final AtomicLong DOCS = new AtomicLong(70000000000000L);
  final JsonMapper json = JsonMapper.builder().build();
  final String password = "Senha-segura-123";
  final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

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

  Map<String, Object> productInput(String sku, String name, String kind, String quantity) {
    return new HashMap<>(
        Map.of(
            "sku",
            sku,
            "name",
            name,
            "kind",
            kind,
            "unit",
            "UN",
            "price",
            "100.00",
            "cost",
            "5",
            "minimum",
            "0",
            "initialQuantity",
            quantity,
            "active",
            true));
  }

  JsonNode product(Browser b, String sku, String kind, String quantity) throws Exception {
    return b.request("POST", "/products", productInput(sku, sku, kind, quantity), 201);
  }

  Map<String, Object> line(String id, String quantity, String price) {
    return Map.of("productId", id, "quantity", quantity, "unitPrice", price);
  }

  Map<String, Object> input(UUID request, String title, List<Map<String, Object>> lines) {
    return new HashMap<>(
        Map.of(
            "requestId",
            request,
            "title",
            title,
            "notes",
            "Escopo combinado com o cliente.",
            "validUntil",
            today.plusDays(10).toString(),
            "items",
            lines));
  }

  JsonNode create(Browser b, Map<String, Object> input) throws Exception {
    return b.request("POST", "/proposals", input, 201);
  }

  JsonNode status(Browser b, JsonNode p, String status, int response) throws Exception {
    return b.request(
        "POST",
        "/proposals/" + p.get("id").asText() + "/status",
        Map.of("version", p.get("version").asLong(), "status", status),
        response);
  }

  Map<String, Object> payment(String account, boolean paid) {
    return Map.of(
        "accountId",
        account,
        "dueDate",
        today.toString(),
        "paid",
        paid,
        "paymentDate",
        today.toString());
  }

  JsonNode approve(Browser b, JsonNode p, String account, boolean paid, int response)
      throws Exception {
    return b.request(
        "POST",
        "/proposals/" + p.get("id").asText() + "/approve",
        Map.of("version", p.get("version").asLong(), "payment", payment(account, paid)),
        response);
  }

  int entries(Browser b) throws Exception {
    return b.request("GET", "/finance/entries", null, 200).get("totalElements").asInt();
  }

  JsonNode detail(Browser b, JsonNode p) throws Exception {
    return b.request("GET", "/proposals/" + p.get("id").asText(), null, 200);
  }

  @Test
  void upgradesExistingProductsAndSaleItemsWithoutChangingStockOrAgreedValues() throws Exception {
    try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
      pool.setJdbcUrl(
          "jdbc:h2:mem:commercial-upgrade-"
              + UUID.randomUUID()
              + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
      pool.setUsername("sa");
      pool.setPassword("");
      pool.setMaximumPoolSize(2);
      org.flywaydb.core.Flyway.configure()
          .dataSource(pool)
          .locations("classpath:db/demo")
          .target("8")
          .load()
          .migrate();
      var company = UUID.randomUUID();
      var product = UUID.randomUUID();
      var sale = UUID.randomUUID();
      try (var c = pool.getConnection();
          var st = c.createStatement()) {
        st.executeUpdate(
            "insert into companies(id,legal_name,document,created_at,updated_at) values('"
                + company
                + "','Legado','98765432000100',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        st.executeUpdate(
            "insert into"
                + " products(id,company_id,sku,name,unit,price,cost,quantity,minimum,active,created_at,updated_at)"
                + " values('"
                + product
                + "','"
                + company
                + "','ANTIGO','Produto"
                + " anterior','UN',10,2,7,1,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        st.executeUpdate(
            "insert into"
                + " sales(id,company_id,number,request_id,fingerprint,status,total,created_at,updated_at)"
                + " values('"
                + sale
                + "','"
                + company
                + "',1,'"
                + UUID.randomUUID()
                + "','legado','DRAFT',20,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        st.executeUpdate(
            "insert into"
                + " sale_items(id,sale_id,product_id,sku,product_name,unit,quantity,unit_price,total,created_at,updated_at)"
                + " values('"
                + UUID.randomUUID()
                + "','"
                + sale
                + "','"
                + product
                + "','ANTIGO','Nome acordado','UN',2,10,20,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      }
      org.flywaydb.core.Flyway.configure()
          .dataSource(pool)
          .locations("classpath:db/demo")
          .load()
          .migrate();
      try (var c = pool.getConnection();
          var st = c.createStatement();
          var r = st.executeQuery("select kind,quantity from products")) {
        assertTrue(r.next());
        assertEquals("PRODUCT", r.getString(1));
        assertEquals(7, r.getBigDecimal(2).intValueExact());
      }
      try (var c = pool.getConnection();
          var st = c.createStatement();
          var r = st.executeQuery("select kind,product_name,total from sale_items")) {
        assertTrue(r.next());
        assertEquals("PRODUCT", r.getString(1));
        assertEquals("Nome acordado", r.getString(2));
        assertEquals(20, r.getBigDecimal(3).intValueExact());
      }
    }
  }

  @Test
  void servicesHaveNoStockAndTypeAndUnitAreImmutable() throws Exception {
    var b = new Browser().signup();
    var service = product(b, "SRV", "SERVICE", "0");
    var item = product(b, "ITEM", "PRODUCT", "5");
    assertFalse(service.get("lowStock").asBoolean());
    assertEquals(
        1, b.request("GET", "/products?kind=SERVICE", null, 200).get("totalElements").asInt());
    assertEquals(
        1, b.request("GET", "/products?kind=PRODUCT", null, 200).get("totalElements").asInt());
    assertEquals(0, b.request("GET", "/products?low=true", null, 200).get("totalElements").asInt());
    assertEquals(
        0, b.request("GET", "/products?kind=SERVICE", null, 200).get("lowStockCount").asInt());
    b.request(
        "POST",
        "/stock/movements",
        Map.of(
            "requestId",
            UUID.randomUUID(),
            "productId",
            service.get("id").asText(),
            "kind",
            "IN",
            "quantity",
            "1",
            "reason",
            "Inválido"),
        400);
    b.request("GET", "/stock/movements?productId=" + service.get("id").asText(), null, 400);
    b.request("POST", "/products", productInput("BAD", "Inválido", "SERVICE", "1"), 400);
    var update = productInput("SRV", "Serviço atualizado", "PRODUCT", "0");
    update.remove("initialQuantity");
    b.request("PUT", "/products/" + service.get("id").asText(), update, 400);
    b.request("POST", "/products", productInput("ITEM", "Outro", "SERVICE", "0"), 409);
  }

  @Test
  void directMixedSalesMoveOnlyPhysicalItemsAndCancelAtomically() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Caixa");
    var s = product(b, "SERV", "SERVICE", "0");
    var p = product(b, "PROD", "PRODUCT", "4");
    var sale =
        b.request(
            "POST",
            "/sales",
            Map.of(
                "requestId",
                UUID.randomUUID(),
                "items",
                List.of(
                    line(s.get("id").asText(), "2", "100"), line(p.get("id").asText(), "1", "20"))),
            201);
    var id = sale.get("id").asText();
    b.request("POST", "/sales/" + id + "/confirm", payment(account, true), 200);
    assertEquals(1, entries(b));
    assertEquals(2, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
    assertEquals(
        220, b.request("GET", "/finance/accounts", null, 200).get(0).get("balance").asDouble());
    b.request("POST", "/sales/" + id + "/cancel", null, 200);
    b.request("POST", "/sales/" + id + "/cancel", null, 200);
    assertEquals(3, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
    assertEquals(
        0, b.request("GET", "/finance/accounts", null, 200).get(0).get("balance").asDouble());
  }

  @Test
  void approvalPreservesAgreedSnapshotsAndCreatesOneConfirmedSaleAndReceivable() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Caixa");
    var product = product(b, "PROD", "PRODUCT", "10");
    var service = product(b, "CONSULT", "SERVICE", "0");
    var content =
        input(
            UUID.randomUUID(),
            "Implantação",
            List.of(
                line(product.get("id").asText(), "2", "10"),
                line(service.get("id").asText(), "1.5", "100")));
    var p = create(b, content);
    assertEquals(0, entries(b));
    assertEquals(0, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
    var change = productInput("CONSULT-NOVO", "Nome alterado", "SERVICE", "0");
    change.remove("initialQuantity");
    change.put("price", "999");
    b.request("PUT", "/products/" + service.get("id").asText(), change, 200);
    var open = status(b, p, "OPEN", 200);
    var result = approve(b, open, account, false, 200);
    var retry = approve(b, open, account, false, 200);
    assertEquals("APPROVED", result.get("status").asText());
    assertEquals(result.get("saleId").asText(), retry.get("saleId").asText());
    assertEquals(1, entries(b));
    var sale = b.request("GET", "/sales/" + result.get("saleId").asText(), null, 200);
    assertEquals(170, sale.get("total").asDouble());
    assertEquals("CONFIRMED", sale.get("status").asText());
    var found = false;
    for (var i : sale.get("items"))
      if (i.get("kind").asText().equals("SERVICE")) {
        assertEquals("CONSULT", i.get("productName").asText());
        assertEquals("CONSULT", i.get("sku").asText());
        assertEquals(100, i.get("unitPrice").asDouble());
        found = true;
      }
    assertTrue(found);
    assertEquals(
        0, b.request("GET", "/finance/accounts", null, 200).get(0).get("balance").asDouble());
    assertEquals(
        "PENDING",
        b.request("GET", "/finance/entries", null, 200)
            .get("content")
            .get(0)
            .get("status")
            .asText());
  }

  @Test
  void equalTotalItemEditBumpsRevisionAndStaleWritesAreRejected() throws Exception {
    var b = new Browser().signup();
    var service = product(b, "EDIT", "SERVICE", "0");
    var request = UUID.randomUUID();
    var original = input(request, "Revisão", List.of(line(service.get("id").asText(), "1", "100")));
    var p = create(b, original);
    var edited = input(request, "Revisão", List.of(line(service.get("id").asText(), "2", "50")));
    var path = "/proposals/" + p.get("id").asText();
    var next = b.request("PUT", path, Map.of("version", 0, "content", edited), 200);
    assertEquals(1, next.get("version").asLong());
    b.request("PUT", path, Map.of("version", 0, "content", original), 409);
    assertEquals(2, detail(b, p).get("items").get(0).get("quantity").asDouble());
    b.request("POST", "/proposals", original, 200);
    b.request("POST", "/proposals", edited, 409);
    var open = status(b, next, "OPEN", 200);
    b.request("PUT", path, Map.of("version", open.get("version").asLong(), "content", edited), 400);
    status(b, next, "CANCELLED", 409);
    var audit =
        b.request(
            "GET",
            "/audit?resource=PROPOSAL&start="
                + today.withDayOfMonth(1)
                + "&end="
                + today.withDayOfMonth(today.lengthOfMonth()),
            null,
            200);
    assertTrue(audit.get("totalElements").asInt() >= 3);
  }

  @Test
  void expiredProposalsNeedRenewalAndClosedProposalsDoNotCreateSales() throws Exception {
    var b = new Browser().signup();
    var service = product(b, "EXP", "SERVICE", "0");
    var account = b.account("Conta");
    var p =
        create(
            b,
            input(
                UUID.randomUUID(),
                "Validade",
                List.of(line(service.get("id").asText(), "1", "100"))));
    var open = status(b, p, "OPEN", 200);
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              var entity = proposals.findById(UUID.fromString(p.get("id").asText())).orElseThrow();
              entity.setValidUntil(today.minusDays(1));
            });
    assertTrue(detail(b, p).get("expired").asBoolean());
    approve(b, open, account, false, 400);
    var draft = status(b, detail(b, p), "DRAFT", 200);
    var cancelled = status(b, draft, "CANCELLED", 200);
    approve(b, cancelled, account, false, 400);
    status(b, cancelled, "OPEN", 400);
    assertEquals(0, entries(b));
    assertEquals(0, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
  }

  @Test
  void unavailableStockRollsBackProposalSaleFinancialAndMovements() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var a = product(b, "A", "PRODUCT", "2");
    var z = product(b, "Z", "PRODUCT", "0");
    var p =
        create(
            b,
            input(
                UUID.randomUUID(),
                "Sem saldo",
                List.of(
                    line(a.get("id").asText(), "1", "10"), line(z.get("id").asText(), "1", "20"))));
    var open = status(b, p, "OPEN", 200);
    approve(b, open, account, true, 400);
    assertEquals("OPEN", detail(b, p).get("status").asText());
    assertEquals(0, entries(b));
    assertEquals(0, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
    assertEquals(1, b.request("GET", "/stock/movements", null, 200).get("totalElements").asInt());
  }

  @Test
  void simultaneousApprovalCreatesOnlyOneSaleAndCancelledSaleCannotBeRecreated() throws Exception {
    var b = new Browser().signup();
    var account = b.account("Conta");
    var service = product(b, "PAR", "SERVICE", "0");
    var p =
        status(
            b,
            create(
                b,
                input(
                    UUID.randomUUID(),
                    "Concorrente",
                    List.of(line(service.get("id").asText(), "1", "100")))),
            "OPEN",
            200);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      Callable<JsonNode> call =
          () -> {
            gate.await();
            return approve(b, p, account, true, 200);
          };
      var a = pool.submit(call);
      var c = pool.submit(call);
      gate.countDown();
      var one = a.get(40, TimeUnit.SECONDS);
      var two = c.get(40, TimeUnit.SECONDS);
      assertEquals(one.get("saleId").asText(), two.get("saleId").asText());
      assertEquals(1, entries(b));
      b.request("POST", "/sales/" + one.get("saleId").asText() + "/cancel", null, 200);
      var retry = approve(b, p, account, true, 200);
      assertEquals("CANCELLED", retry.get("saleStatus").asText());
      assertEquals(1, entries(b));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void validatesLineDataOwnershipRolesAndCsrf() throws Exception {
    var b = new Browser().signup();
    var other = new Browser().signup();
    var service = product(b, "OWN", "SERVICE", "0");
    var account = b.account("Conta");
    var content =
        input(
            UUID.randomUUID(), "Segurança", List.of(line(service.get("id").asText(), "1", "100")));
    var p = create(b, content);
    var id = p.get("id").asText();
    other.request("GET", "/proposals/" + id, null, 404);
    other.request("GET", "/proposals/" + id + "/pdf", null, 404);
    other.request("POST", "/proposals", content, 404);
    var bad =
        input(
            UUID.randomUUID(),
            "Duplicado",
            List.of(
                line(service.get("id").asText(), "1", "10"),
                line(service.get("id").asText(), "1", "10")));
    b.request("POST", "/proposals", bad, 400);
    var nullLine =
        input(
            UUID.randomUUID(), "Nulo", new ArrayList<>(Arrays.asList((Map<String, Object>) null)));
    b.request("POST", "/proposals", nullLine, 400);
    var large =
        input(
            UUID.randomUUID(),
            "Alto",
            List.of(line(service.get("id").asText(), "999999999.999", "999999999999.99")));
    b.request("POST", "/proposals", large, 400);
    var old =
        input(UUID.randomUUID(), "Passado", List.of(line(service.get("id").asText(), "1", "10")));
    old.put("validUntil", today.minusDays(1).toString());
    b.request("POST", "/proposals", old, 400);
    var token = b.token;
    b.token = null;
    b.request("POST", "/proposals", content, 403);
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
    viewer.request("GET", "/proposals/" + id, null, 200);
    viewer.request("POST", "/proposals", content, 403);
    viewer.request(
        "POST",
        "/proposals/" + id + "/approve",
        Map.of("version", 0, "payment", payment(account, false)),
        403);
    viewer.request(
        "PUT",
        "/products/" + service.get("id").asText(),
        productInput("OWN", "Alterado", "SERVICE", "0"),
        403);
  }

  @Test
  void inactiveCatalogOrForeignPaymentAccountCannotBeApproved() throws Exception {
    var b = new Browser().signup();
    var other = new Browser().signup();
    var account = other.account("Outra");
    var service = product(b, "INACTIVE", "SERVICE", "0");
    var p =
        status(
            b,
            create(
                b,
                input(
                    UUID.randomUUID(),
                    "Conta",
                    List.of(line(service.get("id").asText(), "1", "100")))),
            "OPEN",
            200);
    approve(b, p, account, false, 404);
    assertEquals(0, entries(b));
    assertEquals(0, b.request("GET", "/sales", null, 200).get("totalElements").asInt());
    var change = productInput("INACTIVE", "Desativado", "SERVICE", "0");
    change.remove("initialQuantity");
    change.put("active", false);
    b.request("PUT", "/products/" + service.get("id").asText(), change, 200);
    approve(b, p, b.account("Própria"), false, 400);
  }

  @Test
  void pdfHasAllItemsNotesAndMultiplePagesWithoutFinancialEffects() throws Exception {
    var b = new Browser().signup();
    var ls = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 30; i++) {
      var s = product(b, "PDF-" + i, "SERVICE", "0");
      ls.add(line(s.get("id").asText(), "1.25", "89.90"));
    }
    var p = create(b, input(UUID.randomUUID(), "Proposta com conteúdo acentuado 🎮", ls));
    var response =
        b.http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/api/v1/proposals/"
                            + p.get("id").asText()
                            + "/pdf"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, response.statusCode());
    assertTrue(
        response.headers().firstValue("content-disposition").orElse("").contains("attachment"));
    try (var doc = Loader.loadPDF(response.body())) {
      assertTrue(doc.getNumberOfPages() > 1);
      for (var name : doc.getPage(0).getResources().getFontNames())
        assertTrue(doc.getPage(0).getResources().getFont(name).isEmbedded());
      var text = new PDFTextStripper().getText(doc);
      assertTrue(text.contains("PDF-29"));
      assertTrue(text.contains("Escopo combinado"));
      assertTrue(text.contains("TOTAL:"));
      assertTrue(text.contains("Rascunho"));
    }
    assertEquals(0, entries(b));
  }
}
