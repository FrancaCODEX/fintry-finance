package com.fintry.auth;

import com.fintry.audit.AuditService;
import com.fintry.company.repository.CompanyRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {
  private final CurrentUser current;
  private final UserRepository users;
  private final CompanyRepository companies;
  private final AccessTokenRepository tokens;
  private final AccessFlows flows;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final EntityManager em;
  private final com.fintry.subscription.SubscriptionService subscriptions;

  public record InviteInput(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @NotNull AppUser.Role role) {}

  public record UpdateInput(
      @NotBlank @Size(max = 120) String name, @NotNull AppUser.Role role, boolean active) {}

  public record View(
      UUID id,
      String name,
      String email,
      AppUser.Role role,
      boolean active,
      boolean invited,
      Instant invitationExpiresAt) {}

  public record Result(
      List<View> content,
      long totalElements,
      int totalPages,
      int number,
      long active,
      long pending) {}

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
  }

  private ResponseStatusException invalid(String text) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }

  private AppUser lockAdmin() {
    current.requireAdmin();
    var actor = current.get();
    companies.lock(actor.getCompanyId()).orElseThrow();
    em.refresh(actor);
    current.requireAdmin();
    return actor;
  }

  private AppUser member(UUID id, UUID company) {
    var u = users.findByIdAndCompanyId(id, company).orElseThrow(this::missing);
    users.lock(id).orElseThrow(this::missing);
    em.refresh(u);
    return u;
  }

  private Map<String, String> snapshot(AppUser u) {
    return AuditService.fields(
        "Nome",
        u.getName(),
        "E-mail",
        u.getEmail(),
        "Perfil",
        u.getRole(),
        "Acesso",
        u.isInvited() ? "Aguardando ativação" : u.isActive() ? "Ativo" : "Inativo");
  }

  public Result list(String search, AppUser.Role role, int page, int size) {
    current.requireAdmin();
    if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 120))
      throw invalid("Busca ou paginação inválida.");
    UUID company = current.companyId();
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    org.springframework.data.jpa.domain.Specification<AppUser> filter =
        (root, q, cb) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          if (role != null) p.add(cb.equal(root.get("role"), role));
          if (!term.isBlank()) {
            String escaped =
                "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), escaped, '!'),
                    cb.like(root.get("email"), escaped, '!')));
          }
          return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result =
        users.findAll(
            filter,
            org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("name", "id")));
    var ids = result.stream().map(AppUser::getId).toList();
    var expires = new HashMap<UUID, Instant>();
    if (!ids.isEmpty())
      for (var t : tokens.findByUserIdInAndKindAndUsedAtIsNull(ids, AccessToken.Kind.INVITE))
        expires.put(t.getUserId(), t.getExpiresAt());
    return new Result(
        result.stream()
            .map(
                u ->
                    new View(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getRole(),
                        u.isActive(),
                        u.isInvited(),
                        expires.get(u.getId())))
            .toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        users.countByCompanyIdAndActive(company, true),
        users.countByCompanyIdAndInvited(company, true));
  }

  @Transactional
  public AccessFlows.Invitation invite(InviteInput input) {
    var actor = lockAdmin();
    String email = input.email().strip().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Este e-mail não está disponível. Confira os usuários da equipe.");
    subscriptions.check(
        actor.getCompanyId(), com.fintry.subscription.SubscriptionService.Resource.MEMBERS, 1);
    var u = new AppUser();
    u.setCompanyId(actor.getCompanyId());
    u.setName(input.name().strip());
    u.setEmail(email);
    u.setRole(input.role());
    u.setActive(false);
    if (!u.isInvited())
      subscriptions.check(
          actor.getCompanyId(), com.fintry.subscription.SubscriptionService.Resource.MEMBERS, 1);
    u.setInvited(true);
    u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
    users.saveAndFlush(u);
    var link = flows.issueInvite(u);
    audit.changeAs(actor, "INVITE_CREATE", "USER", u.getId(), u.getName(), Map.of(), snapshot(u));
    return link;
  }

  @Transactional
  public void update(UUID id, UpdateInput input) {
    var actor = lockAdmin();
    var u = member(id, actor.getCompanyId());
    if (u.getId().equals(actor.getId()) && (!input.active() || input.role() != AppUser.Role.ADMIN))
      throw invalid("Sua própria conta deve permanecer ativa como Administrador.");
    if (u.isInvited() && input.active())
      throw invalid("Este usuário precisa aceitar um convite antes de acessar.");
    if (u.isActive()
        && u.getRole() == AppUser.Role.ADMIN
        && (!input.active() || input.role() != AppUser.Role.ADMIN)
        && users.countByCompanyIdAndRoleAndActive(u.getCompanyId(), AppUser.Role.ADMIN, true) <= 1)
      throw invalid("Mantenha pelo menos um Administrador ativo.");
    if (input.active() && !u.isActive() && !u.isInvited())
      subscriptions.check(
          actor.getCompanyId(), com.fintry.subscription.SubscriptionService.Resource.MEMBERS, 1);
    var before = snapshot(u);
    u.setName(input.name().strip());
    u.setRole(input.role());
    u.setActive(input.active());
    if (!before.equals(snapshot(u))) {
      u.setAuthVersion(u.getAuthVersion() + 1);
      tokens.revoke(u.getId(), Instant.now());
      audit.changeAs(actor, "UPDATE", "USER", u.getId(), u.getName(), before, snapshot(u));
    }
  }

  @Transactional
  public AccessFlows.Invitation reinvite(UUID id) {
    var actor = lockAdmin();
    var u = member(id, actor.getCompanyId());
    if (u.isActive())
      throw invalid("O usuário já está ativo. Para trocar a senha, use a recuperação de acesso.");
    if (!u.isInvited())
      subscriptions.check(
          actor.getCompanyId(), com.fintry.subscription.SubscriptionService.Resource.MEMBERS, 1);
    u.setInvited(true);
    u.setAuthVersion(u.getAuthVersion() + 1);
    tokens.revoke(u.getId(), Instant.now());
    var invitation = flows.issueInvite(u);
    audit.eventAs(actor, "INVITE_RENEW", "USER", u.getId(), u.getName());
    return invitation;
  }

  @Transactional
  public void revoke(UUID id) {
    var actor = lockAdmin();
    var u = member(id, actor.getCompanyId());
    if (!u.isInvited()) throw invalid("Este usuário não tem convite pendente.");
    tokens.revoke(u.getId(), Instant.now());
    u.setAuthVersion(u.getAuthVersion() + 1);
    u.setInvited(false);
    audit.eventAs(actor, "INVITE_REVOKE", "USER", u.getId(), u.getName());
  }
}
