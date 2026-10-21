package com.fintry.subscription;

import static com.fintry.subscription.SaasInvoice.Status.*;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.billing.AsaasClient;
import com.fintry.billing.BillingConnection.Environment;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class SaasPaymentService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionService access;
  private final PlanRequestRepository requests;
  private final SaasInvoiceRepository invoices;
  private final SaasPaymentsConfig config;
  private final AsaasClient asaas;
  private final AuditService audit;
  private final PlatformTransactionManager transactions;

  @Value("${fintry.saas.mode:EVALUATION}")
  private SubscriptionService.Mode mode;

  public record CheckoutInput(
      @NotNull UUID requestId,
      @NotNull @DecimalMin("5.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
      @NotNull SaasInvoice.Method method,
      @NotBlank @Size(max = 120) String payerName,
      @NotBlank @Pattern(regexp = "[0-9]{11}|[0-9]{14}") String document,
      @AssertTrue boolean accepted) {}

  public record InvoiceView(
      UUID id,
      UUID requestId,
      SaasPlan.Code plan,
      BigDecimal amount,
      LocalDate dueDate,
      SaasInvoice.Method method,
      SaasInvoice.Status status,
      Environment environment,
      String invoiceUrl,
      String message,
      Instant activeUntil,
      LocalDateTime createdAt) {}

  public record View(
      boolean enabled, Environment environment, int periodDays, List<InvoiceView> invoices) {}

  private record Reservation(SaasInvoice invoice, boolean created) {}

  private <T> T tx(Supplier<T> task) {
    return new TransactionTemplate(transactions).execute(s -> task.get());
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensalidade não encontrada.");
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
  }

  private String reference(SaasInvoice i) {
    return "fintry-saas-" + i.getId();
  }

  private InvoiceView view(SaasInvoice i) {
    return new InvoiceView(
        i.getId(),
        i.getRequestId(),
        i.getPlan(),
        i.getAmount(),
        i.getDueDate(),
        i.getMethod(),
        i.getStatus(),
        i.getEnvironment(),
        i.getInvoiceUrl(),
        i.getMessage(),
        i.getActiveUntil(),
        i.getCreatedAt());
  }

  public View list() {
    return tx(
        () ->
            new View(
                config.isEnabled() && mode == SubscriptionService.Mode.COMMERCIAL,
                config.getEnvironment(),
                30,
                invoices
                    .findTop20ByCompanyIdOrderByCreatedAtDescIdDesc(current.companyId())
                    .stream()
                    .map(this::view)
                    .toList()));
  }

  private void configured() {
    if (!config.isEnabled() || mode != SubscriptionService.Mode.COMMERCIAL)
      throw conflict("Cobrança das mensalidades ainda não habilitada pelo operador do Fintry.");
  }

  private void merchant(SaasInvoice i) {
    configured();
    if (!config.getAccount().equals(i.getMerchant())
        || config.getEnvironment() != i.getEnvironment())
      throw conflict(
          "A conta recebedora mudou. O operador precisa consultar a mensalidade na conta"
              + " original.");
  }

  private SaasInvoice owned(UUID id) {
    return invoices.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private SaasInvoice lock(UUID id, UUID company) {
    companies.lock(company).orElseThrow(this::missing);
    return invoices.findByIdAndCompanyId(id, company).orElseThrow(this::missing);
  }

  public InvoiceView checkout(CheckoutInput input) {
    current.requireAdmin();
    configured();
    UUID company = current.companyId();
    var reservation =
        tx(
            () -> {
              companies.lock(company).orElseThrow(this::missing);
              var r =
                  requests
                      .findByIdAndCompanyId(input.requestId(), company)
                      .orElseThrow(this::missing);
              var existing = invoices.findByRequestId(r.getId());
              // A repeated submission must never issue a second payment, even after a timeout.
              if (existing.isPresent()) return new Reservation(existing.get(), false);
              var s = subscriptions.findById(company).orElseThrow();
              if (r.getStatus() != PlanRequest.Status.PENDING
                  || !r.getId().equals(s.getPendingRequestId()))
                throw conflict("A solicitação mudou. Atualize a página.");
              if (s.getState() == SaasSubscription.State.LEGACY
                  || s.getState() == SaasSubscription.State.SIMULATED)
                throw conflict(
                    "Este acesso exige conferência do operador antes da contratação comercial.");
              if (s.getState() == SaasSubscription.State.ACTIVE
                  && s.getPaidUntil() != null
                  && s.getPaidUntil().isAfter(Instant.now())
                  && s.getPlan() != r.getPlan())
                throw conflict(
                    "Mudanças de plano durante um período pago exigem atendimento comercial. Você"
                        + " pode renovar o plano atual.");
              var price = config.price(r.getPlan());
              if (price == null || price.compareTo(input.price()) != 0)
                throw conflict(
                    "O preço mudou ou ainda não foi definido. Atualize a página antes de"
                        + " contratar.");
              access.fits(company, r.getPlan());
              var i = new SaasInvoice();
              i.setCompanyId(company);
              i.setRequestId(r.getId());
              i.setPlan(r.getPlan());
              i.setEnvironment(config.getEnvironment());
              i.setMerchant(config.getAccount());
              i.setAmount(price);
              i.setDueDate(today().plusDays(3));
              i.setMethod(input.method());
              i.setStatus(ISSUING);
              invoices.saveAndFlush(i);
              audit.eventAs(
                  current.get(),
                  "SUBSCRIPTION_INVOICE",
                  "SUBSCRIPTION",
                  i.getId(),
                  "Mensalidade de 30 dias solicitada");
              return new Reservation(i, true);
            });
    var i = reservation.invoice();
    if (!reservation.created()) return view(i);
    try {
      var customer =
          asaas.request(
              i.getEnvironment(),
              config.getApiKey(),
              "POST",
              "/customers",
              Map.of(
                  "name",
                  input.payerName().strip(),
                  "cpfCnpj",
                  input.document(),
                  "notificationDisabled",
                  true,
                  "externalReference",
                  reference(i)));
      String customerId = customer.path("id").asText("");
      if (!customerId.matches("cus_[A-Za-z0-9_-]{1,90}")) throw new AsaasClient.ProviderFailure();
      tx(
          () -> {
            var locked = lock(i.getId(), company);
            locked.setCustomerId(customerId);
            return null;
          });
      var payment =
          asaas.request(
              i.getEnvironment(),
              config.getApiKey(),
              "POST",
              "/payments",
              Map.of(
                  "customer",
                  customerId,
                  "billingType",
                  i.getMethod().name(),
                  "value",
                  i.getAmount(),
                  "dueDate",
                  i.getDueDate().toString(),
                  "description",
                  "Fintry · " + i.getPlan() + " · acesso por 30 dias, sem débito automático",
                  "externalReference",
                  reference(i)));
      String id = payment.path("id").asText("");
      if (!id.matches("pay_[A-Za-z0-9_-]{1,90}")) throw new AsaasClient.ProviderFailure();
      // Creation responses never grant access. Read the authoritative payment first.
      return apply(
          i.getId(),
          company,
          asaas.request(i.getEnvironment(), config.getApiKey(), "GET", "/payments/" + id, null));
    } catch (AsaasClient.ProviderFailure ex) {
      return tx(
          () -> {
            var locked = lock(i.getId(), company);
            if (locked.getActiveUntil() == null && locked.getStatus() != CANCELLED)
              review(locked, "Resposta não confirmada. Use Consultar; não gere outra cobrança.");
            return view(locked);
          });
    }
  }

  public InvoiceView refresh(UUID id) {
    current.requireAdmin();
    return refresh(tx(() -> owned(id)));
  }

  private InvoiceView refresh(SaasInvoice i) {
    merchant(i);
    JsonNode payment;
    if (i.getProviderId() == null) {
      var list =
          asaas.request(
              i.getEnvironment(),
              config.getApiKey(),
              "GET",
              "/payments?externalReference=" + AsaasClient.encode(reference(i)) + "&limit=2",
              null);
      if (list.path("totalCount").asInt(-1) != 1 || list.path("data").size() != 1)
        return tx(
            () -> {
              var locked = lock(i.getId(), i.getCompanyId());
              review(
                  locked,
                  "Não foi possível identificar uma única cobrança. Solicite conferência ao"
                      + " operador.");
              return view(locked);
            });
      String id = list.path("data").get(0).path("id").asText("");
      if (!id.matches("pay_[A-Za-z0-9_-]{1,90}")) throw new AsaasClient.ProviderFailure();
      payment =
          asaas.request(i.getEnvironment(), config.getApiKey(), "GET", "/payments/" + id, null);
    } else
      payment =
          asaas.request(
              i.getEnvironment(),
              config.getApiKey(),
              "GET",
              "/payments/" + i.getProviderId(),
              null);
    return apply(i.getId(), i.getCompanyId(), payment);
  }

  public InvoiceView cancel(UUID id) {
    current.requireAdmin();
    var original = tx(() -> owned(id));
    merchant(original);
    if (original.getProviderId() == null) {
      refresh(original);
      original = tx(() -> owned(id));
    }
    if (original.getProviderId() == null)
      throw conflict("Consulte e identifique a cobrança antes de cancelar.");
    var i =
        tx(
            () -> {
              var locked = lock(id, current.companyId());
              if (locked.getStatus() == CANCELLED) return locked;
              if (locked.getActiveUntil() != null
                  || !Set.of(PENDING, OVERDUE, CANCELLING).contains(locked.getStatus()))
                throw conflict(
                    "Esta mensalidade exige conferência comercial antes do cancelamento.");
              locked.setReleased(true);
              locked.setStatus(CANCELLING);
              return locked;
            });
    if (i.getStatus() == CANCELLED) return view(i);
    // Intent is durable before the external side effect, so an ambiguous deletion cannot grant
    // access.
    asaas.request(
        i.getEnvironment(), config.getApiKey(), "DELETE", "/payments/" + i.getProviderId(), null);
    return refresh(i);
  }

  public void authenticate(String token) {
    configured();
    if (token == null
        || !MessageDigest.isEqual(
            config.getWebhookToken().getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido.");
  }

  public void webhook(String providerId, String externalReference) {
    var i = tx(() -> invoices.findByProviderId(providerId).orElse(null));
    // Handles a webhook arriving between remote creation and the POST response.
    if (i == null
        && externalReference != null
        && externalReference.matches("fintry-saas-[a-fA-F0-9-]{36}")) {
      UUID id;
      try {
        id = UUID.fromString(externalReference.substring(12));
      } catch (IllegalArgumentException ex) {
        return;
      }
      i = tx(() -> invoices.findById(id).orElse(null));
    }
    if (i == null) return;
    merchant(i);
    apply(
        i.getId(),
        i.getCompanyId(),
        asaas.request(
            i.getEnvironment(), config.getApiKey(), "GET", "/payments/" + providerId, null));
  }

  private void review(SaasInvoice i, String message) {
    i.setStatus(REVIEW);
    i.setMessage(message);
  }

  private String safeUrl(JsonNode p) {
    try {
      String raw = p.path("invoiceUrl").asText("");
      var u = java.net.URI.create(raw);
      if (raw.length() <= 2048
          && "https".equals(u.getScheme())
          && u.getUserInfo() == null
          && (u.getPort() == -1 || u.getPort() == 443)
          && Set.of("www.asaas.com", "asaas.com", "sandbox.asaas.com").contains(u.getHost()))
        return raw;
    } catch (IllegalArgumentException ex) {
    }
    return null;
  }

  private InvoiceView apply(UUID id, UUID company, JsonNode p) {
    return tx(
        () -> {
          var i = lock(id, company);
          merchant(i);
          String before = i.getStatus().name();
          try {
            reconcile(i, p);
          } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            review(
                i, "Dados divergentes ou pagamento excepcional. Solicite conferência ao operador.");
          }
          if (!before.equals(i.getStatus().name()))
            audit.subscriptionPayment(company, id, before, i.getStatus().name());
          invoices.flush();
          return view(i);
        });
  }

  private void reconcile(SaasInvoice i, JsonNode p) {
    String id = p.path("id").asText("");
    if (!id.matches("pay_[A-Za-z0-9_-]{1,90}")
        || !reference(i).equals(p.path("externalReference").asText())
        || (i.getProviderId() != null && !i.getProviderId().equals(id))
        || i.getCustomerId() == null
        || !i.getCustomerId().equals(p.path("customer").asText())
        || !i.getMethod().name().equals(p.path("billingType").asText())
        || i.getAmount().compareTo(new BigDecimal(p.path("value").asText("invalid"))) != 0
        || !i.getDueDate().toString().equals(p.path("dueDate").asText()))
      throw new IllegalArgumentException();
    i.setProviderId(id);
    i.setInvoiceUrl(safeUrl(p));
    String status = p.path("status").asText("");
    if (p.path("deleted").asBoolean(false)) {
      i.setReleased(true);
      if (i.getActiveUntil() != null)
        review(
            i, "Cobrança removida após ativação. O período concedido exige conferência comercial.");
      else {
        i.setStatus(CANCELLED);
        i.setMessage(null);
      }
      return;
    }
    if (i.isReleased()) {
      if (i.getStatus() == CANCELLING && Set.of("PENDING", "OVERDUE").contains(status)) {
        i.setMessage("Cancelamento ainda não confirmado. Consulte ou tente cancelar novamente.");
        return;
      }
      review(
          i, "Cobrança em cancelamento ou restaurada. Não concede acesso; solicite conferência.");
      return;
    }
    if (i.getActiveUntil() != null) {
      if (!status.equals("RECEIVED"))
        review(i, "Pagamento alterado após ativação. Confira estornos e o período com o operador.");
      else {
        i.setStatus(PAID);
        i.setMessage(null);
      }
      return; // Permanent per-invoice grant marker; repeated/out-of-order events never extend
      // again.
    }
    if (status.equals("RECEIVED")) {
      LocalDate credit = LocalDate.parse(p.path("creditDate").asText(""));
      BigDecimal net = new BigDecimal(p.path("netValue").asText("invalid"));
      if (credit.isAfter(today())
          || net.signum() < 0
          || net.compareTo(i.getAmount()) > 0
          || net.scale() > 2) throw new IllegalArgumentException();
      if (i.getEnvironment() == Environment.SANDBOX) {
        i.setStatus(TEST_PAID);
        i.setMessage("Pagamento de teste. Não ativa nem renova acesso comercial.");
        return;
      }
      var s = subscriptions.findById(i.getCompanyId()).orElseThrow();
      var r = requests.findById(i.getRequestId()).orElseThrow();
      if (r.getStatus() != PlanRequest.Status.PENDING
          || !r.getId().equals(s.getPendingRequestId())
          || s.getState() == SaasSubscription.State.LEGACY
          || s.getState() == SaasSubscription.State.SIMULATED
          || (s.getState() == SaasSubscription.State.ACTIVE
              && s.getPaidUntil() != null
              && s.getPaidUntil().isAfter(Instant.now())
              && s.getPlan() != i.getPlan())) {
        review(
            i, "Solicitação ou plano mudou. Pagamento recebido exige conferência antes de ativar.");
        return;
      }
      try {
        access.fits(i.getCompanyId(), i.getPlan());
      } catch (ResponseStatusException ex) {
        review(
            i,
            "Pagamento recebido, mas o uso ultrapassa o plano. Solicite ajuste e consulte"
                + " novamente.");
        return;
      }
      Instant start = Instant.now();
      if (s.getPaidUntil() != null && s.getPaidUntil().isAfter(start)) start = s.getPaidUntil();
      Instant end = start.plus(Duration.ofDays(i.getPeriodDays()));
      s.setState(SaasSubscription.State.ACTIVE);
      s.setPlan(i.getPlan());
      s.setPaidUntil(end);
      s.setCancelAtPeriodEnd(false);
      s.setPendingRequestId(null);
      r.setStatus(PlanRequest.Status.APPROVED);
      r.setReference("asaas:" + id);
      r.setGrantedDays(i.getPeriodDays());
      r.setActiveUntil(end);
      i.setActiveUntil(end);
      i.setStatus(PAID);
      i.setMessage(null);
      return;
    }
    i.setMessage(null);
    switch (status) {
      case "PENDING" -> i.setStatus(PENDING);
      case "CONFIRMED" -> i.setStatus(CONFIRMED);
      case "OVERDUE" -> i.setStatus(OVERDUE);
      default ->
          review(
              i,
              "Situação do pagamento exige conferência comercial. Nenhum período adicional foi"
                  + " concedido.");
    }
  }
}
