package com.fintry.billing;

import static com.fintry.billing.BillingCharge.Status.*;

import com.fintry.audit.AuditService;
import com.fintry.auth.*;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.ContactRepository;
import com.fintry.finance.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class BillingService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final EntryRepository entries;
  private final ContactRepository contacts;
  private final BillingConnectionRepository connections;
  private final BillingChargeRepository charges;
  private final BillingSecrets secrets;
  private final AsaasClient client;
  private final AuditService audit;
  private final PlatformTransactionManager transactions;

  public record ConnectionInput(
      @NotNull BillingConnection.Environment environment,
      @NotBlank @Size(min = 10, max = 2000) String apiKey,
      @NotBlank @Size(min = 32, max = 200) String webhookToken) {}

  public record IssueInput(@NotNull UUID entryId, @NotNull BillingCharge.Method method) {}

  public record ConnectionView(
      boolean available, boolean configured, String environment, UUID webhookId, boolean locked) {}

  public record View(
      UUID id,
      UUID entryId,
      String description,
      BigDecimal amount,
      LocalDate dueDate,
      BillingCharge.Method method,
      BillingCharge.Status status,
      String providerStatus,
      String environment,
      String invoiceUrl,
      String bankSlipUrl,
      BigDecimal netAmount,
      LocalDate settledDate,
      UUID feeEntryId,
      String message,
      LocalDateTime updatedAt) {}

  public record PageView(List<View> content, int number, int totalPages, long totalElements) {}

  public record Pix(String payload) {}

  private <T> T tx(java.util.function.Supplier<T> task) {
    return new TransactionTemplate(transactions).execute(s -> task.get());
  }

  private ResponseStatusException invalid(String text) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobrança ou conexão não encontrada.");
  }

  private void lock(UUID company) {
    companies.lock(company).orElseThrow(this::missing);
  }

  private BillingConnection connection(UUID company) {
    return connections
        .findByCompanyIdAndActiveTrue(company)
        .orElseThrow(() -> invalid("Configure a conexão com o Asaas primeiro."));
  }

  private BillingCharge charge(UUID id, UUID company) {
    return charges.findByIdAndCompanyId(id, company).orElseThrow(this::missing);
  }

  private View view(BillingCharge c) {
    return new View(
        c.getId(),
        c.getEntryId(),
        c.getDescription(),
        c.getAmount(),
        c.getDueDate(),
        c.getMethod(),
        c.getStatus(),
        c.getProviderStatus(),
        connections.findById(c.getConnectionId()).orElseThrow().getEnvironment().name(),
        c.getInvoiceUrl(),
        c.getBankSlipUrl(),
        c.getNetAmount(),
        c.getSettledDate(),
        c.getFeeEntryId(),
        c.getMessage(),
        c.getUpdatedAt());
  }

  public ConnectionView configuration() {
    var company = current.companyId();
    return tx(
        () -> {
          var c = connections.findByCompanyIdAndActiveTrue(company);
          return new ConnectionView(
              secrets.available(),
              c.isPresent(),
              c.map(x -> x.getEnvironment().name()).orElse(null),
              c.map(BillingConnection::getId).orElse(null),
              c.isPresent() && charges.existsByConnectionId(c.get().getId()));
        });
  }

  public ConnectionView configure(ConnectionInput input) {
    current.requireAdmin();
    var company = current.companyId();
    tx(
        () -> {
          lock(company);
          var previous = connections.findByCompanyIdAndActiveTrue(company);
          boolean promote =
              previous.isPresent()
                  && previous.get().getEnvironment() == BillingConnection.Environment.SANDBOX
                  && input.environment() == BillingConnection.Environment.PRODUCTION;
          if (previous.isPresent()
              && charges.existsByConnectionId(previous.get().getId())
              && !promote)
            throw invalid(
                "Esta conexão já possui cobranças. Preserve as credenciais; apenas a ativação de"
                    + " Produção após Sandbox é permitida.");
          if (previous.isPresent()
              && previous.get().getEnvironment() == BillingConnection.Environment.PRODUCTION
              && input.environment() != BillingConnection.Environment.PRODUCTION)
            throw invalid("A conexão de produção não pode retornar ao ambiente de testes.");
          if (input.apiKey().equals(input.webhookToken())
              || input.apiKey().contains("\n")
              || input.apiKey().contains("\r")
              || !input.webhookToken().matches("[A-Za-z0-9_-]{32,200}"))
            throw invalid(
                "Use um token de webhook de 32 a 200 letras, números, _ ou -, diferente da chave da"
                    + " API.");
          if (promote) previous.ifPresent(old -> old.setActive(false));
          var c = promote ? new BillingConnection() : previous.orElseGet(BillingConnection::new);
          c.setCompanyId(company);
          c.setEnvironment(input.environment());
          c.setApiKey(secrets.encrypt(input.apiKey(), company, "api"));
          c.setWebhookToken(secrets.encrypt(input.webhookToken(), company, "webhook"));
          connections.save(c);
          audit.change(
              "BILLING_CONNECT",
              "BILLING",
              c.getId(),
              "Conexão Asaas",
              Map.of(),
              AuditService.fields("Ambiente", c.getEnvironment(), "Conexão", "Configurada"));
          return true;
        });
    return configuration();
  }

  public PageView list(int page) {
    if (page < 0) throw invalid("Página inválida.");
    var company = current.companyId();
    return tx(
        () -> {
          var p =
              charges.findByCompanyId(
                  company,
                  PageRequest.of(
                      page, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
          return new PageView(
              p.map(this::view).toList(), p.getNumber(), p.getTotalPages(), p.getTotalElements());
        });
  }

  private record Reserved(UUID id, boolean created) {}

  public View issue(IssueInput input) {
    var actor = current.get();
    var company = actor.getCompanyId();
    var reserve =
        tx(
            () -> {
              lock(company);
              var c = connection(company);
              var e = entries.lock(input.entryId(), company).orElseThrow(this::missing);
              var previous = charges.findByEntryId(e.getId());
              if (previous.isPresent()) {
                if (previous.get().getMethod() != input.method())
                  throw invalid("Este lançamento já possui outra forma de cobrança.");
                return new Reserved(previous.get().getId(), false);
              }
              if (e.getType() != FinancialEntry.Type.INCOME
                  || e.getStatus() != FinancialEntry.Status.PENDING
                  || e.getTransferId() != null) throw invalid("Escolha uma receita pendente.");
              if (e.getDueDate().isBefore(today()))
                throw invalid("Ajuste o vencimento antes de emitir uma cobrança vencida.");
              if (e.getContactId() == null)
                throw invalid("Vincule um cliente com CPF/CNPJ ao lançamento.");
              var contact = contacts.lock(e.getContactId(), company).orElseThrow(this::missing);
              if (contact.getKind() == com.fintry.directory.Contact.Kind.SUPPLIER
                  || !contact.isActive()
                  || contact.getDocument() == null
                  || !contact.getDocument().matches("[0-9]{11}|[0-9]{14}"))
                throw invalid("O cliente precisa estar ativo e ter CPF/CNPJ cadastrado.");
              // This reservation commits BEFORE the first network request. A lost response must
              // never reissue.
              var b = new BillingCharge();
              b.setCompanyId(company);
              b.setConnectionId(c.getId());
              b.setEntryId(e.getId());
              b.setDescription(e.getDescription());
              b.setAmount(e.getAmount());
              b.setDueDate(e.getDueDate());
              b.setMethod(input.method());
              b.setStatus(ISSUING);
              b.setMessage("Emissão em andamento. Se houver interrupção, use Consultar Asaas.");
              charges.saveAndFlush(b);
              audit.change(
                  "BILLING_ISSUE",
                  "BILLING",
                  b.getId(),
                  b.getDescription(),
                  Map.of(),
                  AuditService.fields(
                      "Situação", ISSUING, "Ambiente", c.getEnvironment(), "Valor", b.getAmount()));
              return new Reserved(b.getId(), true);
            });
    if (reserve.created()) {
      try {
        tx(
            () -> {
              lock(company);
              var b = charge(reserve.id(), company);
              var e = entries.lock(b.getEntryId(), company).orElseThrow(this::missing);
              var c = connections.findById(b.getConnectionId()).orElseThrow(this::missing);
              var contact = contacts.lock(e.getContactId(), company).orElseThrow(this::missing);
              var customer =
                  call(
                      c,
                      "POST",
                      "/customers",
                      Map.of(
                          "name",
                          contact.getName(),
                          "cpfCnpj",
                          contact.getDocument(),
                          "notificationDisabled",
                          true,
                          "externalReference",
                          "fintry-" + b.getId()));
              b.setCustomerId(required(customer, "id"));
              var p =
                  call(
                      c,
                      "POST",
                      "/payments",
                      Map.of(
                          "customer",
                          b.getCustomerId(),
                          "billingType",
                          b.getMethod().name(),
                          "value",
                          b.getAmount(),
                          "dueDate",
                          b.getDueDate().toString(),
                          "description",
                          b.getDescription(),
                          "externalReference",
                          reference(b),
                          "fine",
                          Map.of("value", 0),
                          "interest",
                          Map.of("value", 0),
                          "discount",
                          Map.of("value", 0)));
              apply(c, b, e, p);
              return true;
            });
      } catch (RuntimeException ex) {
        tx(
            () -> {
              lock(company);
              var b = charge(reserve.id(), company);
              b.setStatus(REVIEW);
              b.setMessage(
                  "Emissão sem confirmação. Consulte o Asaas; não crie outra cobrança para este"
                      + " recebimento.");
              return true;
            });
      }
    }
    return tx(() -> view(charge(reserve.id(), company)));
  }

  public View refresh(UUID id) {
    var company = current.companyId();
    return synchronize(id, company, false);
  }

  private View synchronize(UUID id, UUID company, boolean cancel) {
    return tx(
        () -> {
          lock(company);
          var b = charge(id, company);
          var e = entries.lock(b.getEntryId(), company).orElseThrow(this::missing);
          var c = connections.findById(b.getConnectionId()).orElseThrow(this::missing);
          JsonNode payment;
          if (b.getProviderId() == null) {
            var found =
                call(
                    c,
                    "GET",
                    "/payments?externalReference="
                        + AsaasClient.encode(reference(b))
                        + "&limit=100",
                    null);
            if (!found.path("data").isArray()
                || found.path("totalCount").asInt(-1) != 1
                || found.path("data").size() != 1) {
              b.setStatus(REVIEW);
              b.setMessage(
                  "Nenhuma cobrança única localizada. Confira a conta Asaas; a emissão não será"
                      + " repetida automaticamente.");
              return view(b);
            }
            payment = found.path("data").get(0);
          } else
            payment = call(c, "GET", "/payments/" + AsaasClient.encode(b.getProviderId()), null);
          apply(c, b, e, payment);
          if (cancel) {
            if (b.getStatus() == CANCELLED) return view(b);
            if (!Set.of(PENDING, OVERDUE).contains(b.getStatus()))
              throw invalid(
                  "Apenas cobranças pendentes ou vencidas podem ser canceladas aqui. Confira o"
                      + " recebimento no Asaas.");
            call(c, "DELETE", "/payments/" + AsaasClient.encode(b.getProviderId()), null);
            apply(
                c,
                b,
                e,
                call(c, "GET", "/payments/" + AsaasClient.encode(b.getProviderId()), null));
          }
          return view(b);
        });
  }

  public View cancel(UUID id) {
    return synchronize(id, current.companyId(), true);
  }

  public Pix pix(UUID id) {
    var company = current.companyId();
    return tx(
        () -> {
          var b = charge(id, company);
          if (b.getMethod() != BillingCharge.Method.PIX
              || b.getProviderId() == null
              || !Set.of(PENDING, OVERDUE).contains(b.getStatus()))
            throw invalid("Pix disponível somente para cobranças pendentes emitidas.");
          var c = connections.findById(b.getConnectionId()).orElseThrow(this::missing);
          return new Pix(
              required(
                  call(
                      c,
                      "GET",
                      "/payments/" + AsaasClient.encode(b.getProviderId()) + "/pixQrCode",
                      null),
                  "payload"));
        });
  }

  public void webhook(UUID connectionId, String token, String providerId) {
    var c =
        tx(
            () ->
                connections
                    .findById(connectionId)
                    .orElseThrow(
                        () ->
                            new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Webhook inválido.")));
    if (token == null
        || token.length() > 200
        || !MessageDigest.isEqual(
            secrets
                .decrypt(c.getWebhookToken(), c.getCompanyId(), "webhook")
                .getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido.");
    // Never trust event status/value. Fetch the resource using this company's own API key.
    var id =
        tx(
            () ->
                charges
                    .findByConnectionIdAndProviderId(c.getId(), providerId)
                    .map(BillingCharge::getId)
                    .orElse(null));
    if (id != null) synchronize(id, c.getCompanyId(), false);
    // Unknown events (including create racing reservation) are reconciled by explicit refresh.
  }

  private JsonNode call(BillingConnection c, String method, String path, Object input) {
    return client.request(
        c.getEnvironment(),
        secrets.decrypt(c.getApiKey(), c.getCompanyId(), "api"),
        method,
        path,
        input);
  }

  private String reference(BillingCharge b) {
    return "fintry-" + b.getId();
  }

  private String required(JsonNode p, String field) {
    String v = p.path(field).asText("");
    if (v.isBlank() || v.length() > 5000) throw new AsaasClient.ProviderFailure();
    return v;
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
  }

  private String url(JsonNode p, String field) {
    String v = p.path(field).asText("");
    if (v.isBlank()) return null;
    try {
      var u = java.net.URI.create(v);
      if (v.length() <= 2048
          && "https".equals(u.getScheme())
          && u.getUserInfo() == null
          && Set.of("www.asaas.com", "asaas.com", "sandbox.asaas.com").contains(u.getHost()))
        return v;
    } catch (IllegalArgumentException ex) {
    }
    return null;
  }

  private void review(BillingCharge b, String message) {
    b.setStatus(REVIEW);
    b.setMessage(message);
  }

  private void apply(BillingConnection c, BillingCharge b, FinancialEntry e, JsonNode p) {
    String old = b.getStatus().name();
    try {
      String id = required(p, "id");
      if (id.length() > 100
          || !reference(b).equals(required(p, "externalReference"))
          || (b.getProviderId() != null && !b.getProviderId().equals(id))
          || (b.getCustomerId() != null && !b.getCustomerId().equals(required(p, "customer")))
          || !b.getMethod().name().equals(required(p, "billingType"))
          || b.getAmount().compareTo(new BigDecimal(required(p, "value"))) != 0
          || !b.getDueDate().toString().equals(required(p, "dueDate"))) {
        review(
            b,
            "Os dados do Asaas divergem do lançamento. Revise valor, vencimento e vínculo antes de"
                + " continuar.");
        return;
      }
      b.setProviderId(id);
      b.setCustomerId(required(p, "customer"));
      String status = required(p, "status");
      if (status.length() > 80) throw new IllegalArgumentException();
      b.setProviderStatus(status);
      b.setInvoiceUrl(url(p, "invoiceUrl"));
      b.setBankSlipUrl(url(p, "bankSlipUrl"));
      if (p.path("deleted").asBoolean(false)) status = "DELETED";
      if (b.getSettledDate() != null && !status.equals("RECEIVED")) {
        review(
            b,
            "O recebimento mudou no Asaas. Confira estorno ou contestação; o financeiro foi"
                + " preservado para revisão.");
      } else
        switch (status) {
          case "PENDING" -> {
            b.setStatus(PENDING);
            b.setMessage(null);
          }
          case "OVERDUE" -> {
            b.setStatus(OVERDUE);
            b.setMessage(null);
          }
          case "CONFIRMED" -> {
            b.setStatus(CONFIRMED);
            b.setMessage("Pagamento confirmado, aguardando disponibilização no Asaas.");
          }
          case "DELETED" -> {
            b.setStatus(CANCELLED);
            b.setMessage(
                "Cobrança cancelada no Asaas. O lançamento permanece disponível no financeiro.");
          }
          case "RECEIVED" -> {
            if (c.getEnvironment() == BillingConnection.Environment.SANDBOX) {
              b.setStatus(TEST_RECEIVED);
              b.setMessage(
                  "Pagamento de teste. Nenhuma baixa ou tarifa foi registrada no financeiro.");
            } else settle(b, e, p);
          }
          default ->
              review(
                  b,
                  "Situação exige revisão no Asaas. Nenhuma baixa ou estorno automático foi"
                      + " realizado.");
        }
    } catch (AsaasClient.ProviderFailure
        | IllegalArgumentException
        | DateTimeException
        | ArithmeticException ex) {
      review(b, "Resposta incompleta do Asaas. Confira os dados antes de continuar.");
    } finally {
      if (!old.equals(b.getStatus().name()))
        audit.billingSystem(
            b.getCompanyId(), b.getId(), b.getDescription(), old, b.getStatus().name());
    }
  }

  private void settle(BillingCharge b, FinancialEntry e, JsonNode p) {
    var net =
        new BigDecimal(required(p, "netValue")).setScale(2, java.math.RoundingMode.UNNECESSARY);
    var date = LocalDate.parse(required(p, "creditDate"));
    if (date.isAfter(today()) || net.signum() < 0 || net.compareTo(b.getAmount()) > 0) {
      review(b, "Valor líquido ou data de crédito inválidos. Confira o Asaas.");
      return;
    }
    if (b.getSettledDate() != null) {
      if (!b.getSettledDate().equals(date) || b.getNetAmount().compareTo(net) != 0) {
        review(b, "Data ou tarifa mudou após a baixa. Revise o recebimento no financeiro.");
        return;
      }
      b.setStatus(RECEIVED);
      b.setMessage(null);
      return;
    }
    if (e.getStatus() != FinancialEntry.Status.PENDING
        || e.getAmount().compareTo(b.getAmount()) != 0) {
      review(b, "O lançamento não está pendente ou seu valor diverge. Revise antes de dar baixa.");
      return;
    }
    e.setStatus(FinancialEntry.Status.PAID);
    e.setPaymentDate(date);
    BigDecimal fee = b.getAmount().subtract(net);
    if (fee.signum() > 0) {
      var f = new FinancialEntry();
      f.setCompanyId(b.getCompanyId());
      f.setAccountId(e.getAccountId());
      f.setType(FinancialEntry.Type.EXPENSE);
      f.setDescription("Tarifa Asaas · " + b.getId());
      f.setCategory("Tarifas de cobrança");
      f.setAmount(fee);
      f.setDueDate(date);
      f.setPaymentDate(date);
      f.setStatus(FinancialEntry.Status.PAID);
      entries.saveAndFlush(f);
      b.setFeeEntryId(f.getId());
    }
    b.setSettledDate(date);
    b.setNetAmount(net);
    b.setStatus(RECEIVED);
    b.setMessage(null);
  }
}
