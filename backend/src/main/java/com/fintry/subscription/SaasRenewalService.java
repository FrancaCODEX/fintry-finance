package com.fintry.subscription;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.billing.BillingConnection.Environment;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SaasRenewalService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final SubscriptionRepository subscriptions;
  private final SaasRenewalRepository renewals;
  private final SaasInvoiceRepository invoices;
  private final PlanRequestRepository requests;
  private final SaasPaymentsConfig config;
  private final SubscriptionService access;
  private final SaasPaymentService payments;
  private final AuditService audit;
  private final PlatformTransactionManager transactions;

  @Value("${fintry.saas.mode:EVALUATION}")
  private SubscriptionService.Mode mode;

  @Value("${fintry.saas.renewals.scheduler-enabled:false}")
  private boolean schedulerEnabled;

  public record Consent(
      @NotNull UUID sourceInvoiceId,
      @NotNull @DecimalMin("5.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
      @Min(0) long revision,
      @AssertTrue boolean accepted) {}

  public record Revision(@Min(0) long revision) {}

  public record View(
      boolean enabled,
      long revision,
      String status,
      BigDecimal amount,
      SaasPlan.Code plan,
      SaasInvoice.Method method,
      Instant nextDueAt,
      Instant generationAt,
      UUID invoiceId,
      UUID sourceInvoiceId,
      BigDecimal offeredPrice,
      boolean canEnable,
      boolean schedulerEnabled) {}

  private <T> T tx(Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(s -> action.get());
  }

  private ResponseStatusException conflict(String text) {
    return new ResponseStatusException(HttpStatus.CONFLICT, text);
  }

  private boolean configured() {
    return mode == SubscriptionService.Mode.COMMERCIAL
        && config.isEnabled()
        && config.getEnvironment() == Environment.PRODUCTION;
  }

  private boolean sameMerchant(SaasRenewal r) {
    return r.getMerchant().equals(config.getAccount())
        && r.getEnvironment() == config.getEnvironment();
  }

  private boolean samePrice(SaasRenewal r) {
    var p = config.price(r.getPlan());
    return p != null && p.compareTo(r.getAmount()) == 0;
  }

  private boolean paid(SaasInvoice i) {
    return i.getStatus() == SaasInvoice.Status.PAID
        && i.getActiveUntil() != null
        && !i.isReleased();
  }

  private SaasInvoice candidate(UUID company, SaasSubscription s) {
    return invoices.findTop20ByCompanyIdOrderByCreatedAtDescIdDesc(company).stream()
        .filter(
            i ->
                paid(i)
                    && i.getPlan() == s.getPlan()
                    && i.getEnvironment() == Environment.PRODUCTION
                    && i.getMerchant().equals(config.getAccount()))
        .findFirst()
        .orElse(null);
  }

  private String status(SaasRenewal r, SaasSubscription s) {
    if (r == null || !r.isEnabled()) return "DISABLED";
    if (!configured() || !sameMerchant(r)) return "CONFIGURATION";
    if (!samePrice(r)) return "PRICE_CHANGED";
    if (s.getState() != SaasSubscription.State.ACTIVE
        || s.getPlan() != r.getPlan()
        || s.getPaidUntil() == null) return "PLAN_CHANGED";
    var source = invoices.findById(r.getSourceInvoiceId()).orElseThrow();
    if (!paid(source)) return "REVIEW";
    if (s.getPendingRequestId() != null) return "PENDING";
    try {
      access.fits(s.getCompanyId(), s.getPlan());
    } catch (ResponseStatusException ex) {
      return "CAPACITY";
    }
    if (s.getPaidUntil().equals(r.getLastPeriodEnd())) return "CYCLE_CLOSED";
    return "AUTHORIZED";
  }

  private View view(UUID company) {
    var s = subscriptions.findById(company).orElseThrow();
    var r = renewals.findById(company).orElse(null);
    var source = candidate(company, s);
    var price = config.price(s.getPlan());
    boolean can =
        configured()
            && s.getState() == SaasSubscription.State.ACTIVE
            && s.getPaidUntil() != null
            && s.getPaidUntil().isAfter(Instant.now())
            && source != null
            && price != null;
    return new View(
        r != null && r.isEnabled(),
        r == null ? 0 : r.getRevision(),
        status(r, s),
        r == null ? null : r.getAmount(),
        r == null ? s.getPlan() : r.getPlan(),
        r == null ? null : r.getMethod(),
        s.getPaidUntil(),
        s.getPaidUntil() == null ? null : s.getPaidUntil().minus(Duration.ofDays(3)),
        r == null ? null : r.getLastInvoiceId(),
        source == null ? null : source.getId(),
        price,
        can,
        schedulerEnabled);
  }

  public View current() {
    return tx(() -> view(current.companyId()));
  }

  public View enable(Consent input) {
    current.requireAdmin();
    if (!configured())
      throw conflict("A recorrência exige cobrança comercial configurada em produção.");
    // Reconfirm receipt before accepting a mandate. This never charges or grants an extra period.
    payments.refresh(input.sourceInvoiceId());
    return tx(
        () -> {
          UUID company = current.companyId();
          companies.lock(company).orElseThrow();
          var s = subscriptions.findById(company).orElseThrow();
          var source =
              invoices
                  .findByIdAndCompanyId(input.sourceInvoiceId(), company)
                  .orElseThrow(
                      () ->
                          new ResponseStatusException(
                              HttpStatus.NOT_FOUND, "Mensalidade não encontrada."));
          if (!paid(source)
              || source.getPlan() != s.getPlan()
              || source.getEnvironment() != Environment.PRODUCTION
              || !source.getMerchant().equals(config.getAccount())
              || s.getState() != SaasSubscription.State.ACTIVE
              || s.getPaidUntil() == null
              || !s.getPaidUntil().isAfter(Instant.now()))
            throw conflict(
                "Confirme uma mensalidade paga do plano atual antes de autorizar a recorrência.");
          var price = config.price(s.getPlan());
          if (price == null || price.compareTo(input.price()) != 0)
            throw conflict("O preço mudou. Atualize e confira o novo valor antes de autorizar.");
          access.fits(company, s.getPlan());
          var r = renewals.findById(company).orElse(null);
          if ((r == null ? 0 : r.getRevision()) != input.revision())
            throw conflict("A autorização mudou. Atualize a página.");
          if (r == null) {
            r = new SaasRenewal();
            r.setCompanyId(company);
          }
          r.setEnabled(true);
          r.setPlan(s.getPlan());
          r.setAmount(price);
          r.setMethod(source.getMethod());
          r.setMerchant(source.getMerchant());
          r.setEnvironment(source.getEnvironment());
          r.setCustomerId(source.getCustomerId());
          r.setSourceInvoiceId(source.getId());
          r.setAcceptedAt(Instant.now());
          r.setAcceptedBy(current.get().getId());
          r.setNextCheckAt(Instant.now());
          renewals.saveAndFlush(r);
          s.setCancelAtPeriodEnd(false);
          audit.change(
              "SUBSCRIPTION_RENEWAL_ENABLE",
              "SUBSCRIPTION",
              company,
              "Recorrência autorizada",
              Map.of(),
              AuditService.fields(
                  "Plano",
                  r.getPlan(),
                  "Valor por 30 dias",
                  r.getAmount(),
                  "Pagamento",
                  r.getMethod()));
          subscriptions.flush();
          return view(company);
        });
  }

  public View disable(Revision input) {
    current.requireAdmin();
    return tx(
        () -> {
          UUID company = current.companyId();
          companies.lock(company).orElseThrow();
          var r = renewals.findById(company).orElse(null);
          if (r != null && r.isEnabled()) {
            if (r.getRevision() != input.revision())
              throw conflict("A autorização mudou. Atualize a página.");
            r.setEnabled(false);
            renewals.flush();
            var s = subscriptions.findById(company).orElseThrow();
            s.setCancelAtPeriodEnd(true);
            audit.eventAs(
                current.get(),
                "SUBSCRIPTION_RENEWAL_DISABLE",
                "SUBSCRIPTION",
                company,
                "Emissões recorrentes interrompidas");
          }
          return view(company);
        });
  }

  public View check() {
    current.requireAdmin();
    processCompany(current.companyId());
    return current();
  }

  // Durable reservation commits before the POST. Repeated workers and process restarts never
  // reissue it.
  public boolean processCompany(UUID company) {
    if (!configured()) return false;
    var invoice = tx(() -> reserve(company));
    if (invoice == null) return false;
    payments.issueReserved(invoice);
    return true;
  }

  private SaasInvoice reserve(UUID company) {
    companies.lock(company).orElseThrow();
    var r = renewals.findById(company).orElse(null);
    if (r == null || !r.isEnabled()) return null;
    r.setNextCheckAt(Instant.now().plus(Duration.ofHours(1)));
    var s = subscriptions.findById(company).orElseThrow();
    if (!status(r, s).equals("AUTHORIZED")
        || s.isCancelAtPeriodEnd()
        || Instant.now().isBefore(s.getPaidUntil().minus(Duration.ofDays(3)))) return null;
    try {
      access.fits(company, s.getPlan());
    } catch (ResponseStatusException ex) {
      return null;
    }
    var request = new PlanRequest();
    request.setCompanyId(company);
    request.setPlan(r.getPlan());
    request.setStatus(PlanRequest.Status.PENDING);
    requests.saveAndFlush(request);
    s.setPendingRequestId(request.getId());
    var i = new SaasInvoice();
    i.setCompanyId(company);
    i.setRequestId(request.getId());
    i.setPlan(r.getPlan());
    i.setAmount(r.getAmount());
    i.setEnvironment(r.getEnvironment());
    i.setMerchant(r.getMerchant());
    i.setCustomerId(r.getCustomerId());
    i.setMethod(r.getMethod());
    var date = s.getPaidUntil().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
    var minimum = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
    i.setDueDate(date.isAfter(minimum) ? date : minimum);
    i.setRenewalPeriodEnd(s.getPaidUntil());
    i.setStatus(SaasInvoice.Status.ISSUING);
    invoices.saveAndFlush(i);
    r.setLastPeriodEnd(s.getPaidUntil());
    r.setLastInvoiceId(i.getId());
    audit.subscriptionRenewal(
        company, i.getId(), "Mensalidade recorrente reservada · " + r.getPlan());
    return i;
  }

  public void processDue() {
    if (!configured()) return;
    var ids = tx(() -> renewals.due(Instant.now(), PageRequest.of(0, 100)));
    for (UUID id : ids) {
      try {
        processCompany(id);
      } catch (RuntimeException ex) {
        // Do not log provider payloads, payer data or credentials. Other companies must continue.
        LoggerFactory.getLogger(getClass())
            .warn("Falha ao verificar uma renovação; confira as mensalidades no painel.");
      }
    }
  }
}
