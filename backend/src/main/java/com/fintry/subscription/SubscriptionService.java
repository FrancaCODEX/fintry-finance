package com.fintry.subscription;

import com.fintry.attachment.AttachmentRepository;
import com.fintry.audit.AuditService;
import com.fintry.auth.*;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.finance.AccountRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {
  public enum Mode {
    EVALUATION,
    COMMERCIAL
  }

  public enum Resource {
    MEMBERS,
    ACCOUNTS,
    STORAGE
  }

  private final CurrentUser current;
  private final CompanyRepository companies;
  private final UserRepository users;
  private final AccountRepository accounts;
  private final AttachmentRepository attachments;
  private final SubscriptionRepository subscriptions;
  private final PlanRepository plans;
  private final PlanRequestRepository requests;
  private final AuditService audit;

  @Value("${fintry.saas.mode:EVALUATION}")
  private Mode mode;

  public record PlanView(
      SaasPlan.Code code,
      String name,
      String description,
      int members,
      int accounts,
      long storageBytes,
      java.math.BigDecimal monthlyPrice) {}

  public record Usage(long members, long accounts, long storageBytes) {}

  public record RequestView(
      UUID id,
      SaasPlan.Code plan,
      PlanRequest.Status status,
      LocalDateTime createdAt,
      Instant activeUntil) {}

  public record View(
      UUID companyId,
      Mode mode,
      SaasPlan.Code plan,
      String status,
      boolean writable,
      boolean limited,
      Instant trialEndsAt,
      Instant paidUntil,
      boolean cancelAtPeriodEnd,
      long revision,
      Usage usage,
      List<PlanView> plans,
      List<RequestView> requests) {}

  public record ChangeInput(@NotNull SaasPlan.Code plan, @Min(0) long revision) {}

  public record RevisionInput(@Min(0) long revision) {}

  private ResponseStatusException invalid(String text) {
    return new ResponseStatusException(HttpStatus.CONFLICT, text);
  }

  private SaasSubscription subscription(UUID company) {
    return subscriptions
        .findById(company)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura indisponível. Confira as migrações do ambiente."));
  }

  private SaasPlan plan(SaasPlan.Code code) {
    return plans
        .findById(code)
        .orElseThrow(() -> new IllegalStateException("Catálogo de planos indisponível."));
  }

  private Usage usage(UUID company) {
    return new Usage(
        users.subscriptionSeats(company),
        accounts.countByCompanyId(company),
        attachments.subscriptionBytes(company));
  }

  public String status(SaasSubscription s) {
    if (mode == Mode.EVALUATION)
      return s.getState() == SaasSubscription.State.SIMULATED ? "SIMULATED" : "EVALUATION";
    return switch (s.getState()) {
      case LEGACY -> "LEGACY";
      case SIMULATED -> "INACTIVE";
      case TRIAL -> Instant.now().isBefore(s.getTrialEndsAt()) ? "TRIAL" : "EXPIRED";
      case ACTIVE ->
          s.getPaidUntil() != null && Instant.now().isBefore(s.getPaidUntil())
              ? "ACTIVE"
              : s.isCancelAtPeriodEnd() ? "CANCELLED" : "EXPIRED";
    };
  }

  private boolean writable(SaasSubscription s) {
    return Set.of("EVALUATION", "SIMULATED", "LEGACY", "TRIAL", "ACTIVE").contains(status(s));
  }

  private boolean limited(SaasSubscription s) {
    return !Set.of("EVALUATION", "LEGACY").contains(status(s));
  }

  public record Access(String status, boolean writable, Mode mode, Instant trialEndsAt) {}

  public Access access() {
    var s = subscription(current.companyId());
    return new Access(status(s), writable(s), mode, s.getTrialEndsAt());
  }

  public boolean writable() {
    return writable(subscription(current.companyId()));
  }

  @Transactional
  public void initialize(UUID company) {
    if (subscriptions.existsById(company)) return;
    var s = new SaasSubscription();
    s.setCompanyId(company);
    s.setPlan(SaasPlan.Code.PRO);
    s.setState(SaasSubscription.State.TRIAL);
    s.setTrialEndsAt(Instant.now().plus(Duration.ofDays(14)));
    subscriptions.saveAndFlush(s);
  }

  private View view(UUID company) {
    var s = subscription(company);
    var catalog =
        Arrays.stream(SaasPlan.Code.values())
            .map(this::plan)
            .map(
                p ->
                    new PlanView(
                        p.getCode(),
                        p.getName(),
                        p.getDescription(),
                        p.getMemberLimit(),
                        p.getAccountLimit(),
                        p.getStorageBytes(),
                        p.getMonthlyPrice()))
            .toList();
    return new View(
        company,
        mode,
        s.getPlan(),
        status(s),
        writable(s),
        limited(s),
        s.getTrialEndsAt(),
        s.getPaidUntil(),
        s.isCancelAtPeriodEnd(),
        s.getRevision(),
        usage(company),
        catalog,
        requests.findTop10ByCompanyIdOrderByCreatedAtDescIdDesc(company).stream()
            .map(
                r ->
                    new RequestView(
                        r.getId(),
                        r.getPlan(),
                        r.getStatus(),
                        r.getCreatedAt(),
                        r.getActiveUntil()))
            .toList());
  }

  public View current() {
    return view(current.companyId());
  }

  // Caller transactions retain this company's lock until resource insertion/activation commits.
  @Transactional(propagation = Propagation.MANDATORY)
  public void check(UUID company, Resource resource, long amount) {
    companies.lock(company).orElseThrow();
    var s = subscription(company);
    if (!writable(s))
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED,
          "Seu período terminou. Consulte e exporte seus dados e escolha um plano em Plano e"
              + " assinatura.");
    if (!limited(s)) return;
    var u = usage(company);
    var p = plan(s.getPlan());
    long used =
        switch (resource) {
          case MEMBERS -> u.members();
          case ACCOUNTS -> u.accounts();
          case STORAGE -> u.storageBytes();
        };
    long maximum =
        switch (resource) {
          case MEMBERS -> p.getMemberLimit();
          case ACCOUNTS -> p.getAccountLimit();
          case STORAGE -> p.getStorageBytes();
        };
    if (amount < 0 || amount > maximum - used)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Limite do plano atingido para "
              + switch (resource) {
                case MEMBERS -> "pessoas (incluindo convites pendentes)";
                case ACCOUNTS -> "contas financeiras";
                case STORAGE -> "armazenamento de comprovantes";
              }
              + ". Confira seu uso em Plano e assinatura.");
  }

  private SaasSubscription lock(long revision) {
    current.requireAdmin();
    var company = current.companyId();
    companies.lock(company).orElseThrow();
    var s = subscription(company);
    if (s.getRevision() != revision)
      throw invalid("A assinatura mudou. Atualize a página antes de continuar.");
    return s;
  }

  private void fits(UUID company, SaasPlan.Code code) {
    var u = usage(company);
    var p = plan(code);
    if (u.members() > p.getMemberLimit()
        || u.accounts() > p.getAccountLimit()
        || u.storageBytes() > p.getStorageBytes())
      throw invalid(
          "O uso atual ultrapassa o plano escolhido. Reduza contas, pessoas ou comprovantes antes"
              + " de solicitar essa mudança.");
  }

  private void record(String action, SaasSubscription s, Map<String, String> before) {
    audit.change(
        action,
        "SUBSCRIPTION",
        s.getCompanyId(),
        "Plano do Fintry",
        before,
        AuditService.fields(
            "Plano",
            s.getPlan(),
            "Situação",
            s.getState(),
            "Encerramento programado",
            s.isCancelAtPeriodEnd()));
  }

  @Transactional
  public View simulate(ChangeInput input) {
    if (mode != Mode.EVALUATION)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Simulação disponível somente no ambiente de avaliação.");
    var s = lock(input.revision());
    fits(s.getCompanyId(), input.plan());
    var before = AuditService.fields("Plano", s.getPlan(), "Situação", s.getState());
    s.setPlan(input.plan());
    s.setState(SaasSubscription.State.SIMULATED);
    record("SUBSCRIPTION_SIMULATE", s, before);
    subscriptions.flush();
    return view(s.getCompanyId());
  }

  @Transactional
  public View request(ChangeInput input) {
    current.requireAdmin();
    var company = current.companyId();
    companies.lock(company).orElseThrow();
    var s = subscription(company);
    if (mode != Mode.COMMERCIAL) throw invalid("Use Experimentar plano no ambiente de avaliação.");
    if (s.getPendingRequestId() != null) {
      var r = requests.findById(s.getPendingRequestId()).orElseThrow();
      if (r.getStatus() == PlanRequest.Status.PENDING && r.getPlan() == input.plan())
        return view(company);
      throw invalid("Cancele a solicitação pendente antes de escolher outro plano.");
    }
    if (s.getRevision() != input.revision())
      throw invalid("A assinatura mudou. Atualize a página.");
    fits(company, input.plan());
    var r = new PlanRequest();
    r.setCompanyId(company);
    r.setPlan(input.plan());
    r.setStatus(PlanRequest.Status.PENDING);
    requests.saveAndFlush(r);
    s.setPendingRequestId(r.getId());
    audit.change(
        "SUBSCRIPTION_REQUEST",
        "SUBSCRIPTION",
        r.getId(),
        "Solicitação de plano",
        Map.of(),
        AuditService.fields("Plano", input.plan(), "Situação", "Aguardando ativação"));
    subscriptions.flush();
    return view(company);
  }

  @Transactional
  public View cancelRequest(UUID id, RevisionInput input) {
    var s = lock(input.revision());
    var r =
        requests
            .findByIdAndCompanyId(id, s.getCompanyId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Solicitação não encontrada."));
    if (r.getStatus() != PlanRequest.Status.PENDING || !id.equals(s.getPendingRequestId()))
      throw invalid("Esta solicitação não está pendente.");
    r.setStatus(PlanRequest.Status.CANCELLED);
    s.setPendingRequestId(null);
    audit.eventAs(
        current.get(),
        "SUBSCRIPTION_REQUEST_CANCEL",
        "SUBSCRIPTION",
        r.getId(),
        "Solicitação cancelada");
    subscriptions.flush();
    return view(s.getCompanyId());
  }

  @Transactional
  public View cancellation(RevisionInput input, boolean cancel) {
    var s = lock(input.revision());
    if (mode != Mode.COMMERCIAL || s.getState() != SaasSubscription.State.ACTIVE || !writable(s))
      throw invalid("Não há um período de assinatura ativo para esta ação.");
    var before = AuditService.fields("Encerramento programado", s.isCancelAtPeriodEnd());
    s.setCancelAtPeriodEnd(cancel);
    record(cancel ? "SUBSCRIPTION_CANCEL" : "SUBSCRIPTION_KEEP", s, before);
    subscriptions.flush();
    return view(s.getCompanyId());
  }
}
