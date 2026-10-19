package com.fintry.auth;

import com.fintry.audit.AuditService;
import com.fintry.company.repository.CompanyRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessFlows {
  private final UserRepository users;
  private final AccessTokenRepository tokens;
  private final CurrentUser current;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final EntityManager em;
  private final CompanyRepository companies;
  private final ApplicationEventPublisher publisher;

  @Value("${fintry.mail.enabled:false}")
  private boolean mailEnabled;

  @Value("${fintry.base-url:http://localhost:8080}")
  private String baseUrl;

  private final SecureRandom random = new SecureRandom();

  public record EmailInput(@NotBlank @Email @Size(max = 255) String email) {}

  public record TokenInput(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}

  public record ResetInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
      @NotBlank @Size(min = 10, max = 64) String password) {}

  public record AcceptInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(min = 10, max = 64) String password) {}

  public record ChangeInput(
      @NotBlank @Size(max = 64) String currentPassword,
      @NotBlank @Size(min = 10, max = 64) String password) {}

  public record Invitation(String link, Instant expiresAt) {}

  public record InvitationInfo(
      String name, String email, String companyName, AppUser.Role role, Instant expiresAt) {}

  private ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Link inválido, expirado ou já utilizado. Solicite um novo link.");
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @jakarta.annotation.PostConstruct
  void validateBase() {
    var uri = java.net.URI.create(baseUrl);
    if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalStateException(
          "Configure fintry.base-url com uma URL HTTP(S) sem credenciais, query ou fragmento.");
    baseUrl = baseUrl.replaceAll("/+$", "");
  }

  private Invitation issue(AppUser user, AccessToken.Kind kind, Duration duration) {
    tokens.revokeKind(user.getId(), kind, Instant.now());
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var token = new AccessToken();
    token.setUserId(user.getId());
    token.setKind(kind);
    token.setTokenHash(hash(raw));
    token.setCreatedAt(Instant.now());
    token.setExpiresAt(token.getCreatedAt().plus(duration));
    tokens.save(token);
    return new Invitation(
        baseUrl + "/#" + (kind == AccessToken.Kind.INVITE ? "invite" : "reset") + "=" + raw,
        token.getExpiresAt());
  }

  @Transactional
  public Invitation issueInvite(AppUser user) {
    return issue(user, AccessToken.Kind.INVITE, Duration.ofDays(7));
  }

  @Transactional
  public void forgot(String email) {
    if (!mailEnabled)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Recuperação por e-mail indisponível neste ambiente. Fale com o administrador.");
    var found = users.findByEmail(email.strip().toLowerCase(Locale.ROOT));
    if (found.isEmpty()) return;
    var user = users.lock(found.get().getId()).orElseThrow();
    em.refresh(user);
    if (!user.isActive() || user.isInvited()) return;
    var invitation = issue(user, AccessToken.Kind.RESET, Duration.ofMinutes(15));
    audit.system(
        user.getCompanyId(), "RESET_REQUEST", user.getId(), "Solicitação de recuperação de acesso");
    publisher.publishEvent(new RecoveryMail.Message(user.getEmail(), invitation.link()));
  }

  private AccessToken valid(String raw, AccessToken.Kind kind, boolean lock) {
    String hash = hash(raw);
    var value = lock ? tokens.lock(hash) : tokens.findByTokenHash(hash);
    var token = value.orElseThrow(this::invalid);
    if (token.getKind() != kind
        || token.getUsedAt() != null
        || !token.getExpiresAt().isAfter(Instant.now())) throw invalid();
    return token;
  }

  private AppUser owner(String raw) {
    var id = tokens.owner(hash(raw)).orElseThrow(this::invalid);
    var user = users.lock(id).orElseThrow(this::invalid);
    em.refresh(user);
    return user;
  }

  public InvitationInfo inspect(String raw) {
    var token = valid(raw, AccessToken.Kind.INVITE, false);
    var user = users.findById(token.getUserId()).orElseThrow(this::invalid);
    if (!user.isInvited() || user.isActive()) throw invalid();
    var company = companies.findById(user.getCompanyId()).orElseThrow();
    return new InvitationInfo(
        user.getName(),
        user.getEmail(),
        company.getTradeName(),
        user.getRole(),
        token.getExpiresAt());
  }

  @Transactional
  public void reset(ResetInput input) {
    AuthService.validatePassword(input.password());
    var user = owner(input.token());
    var token = valid(input.token(), AccessToken.Kind.RESET, true);
    if (!user.isActive() || user.isInvited()) throw invalid();
    user.setPasswordHash(encoder.encode(input.password()));
    user.setAuthVersion(user.getAuthVersion() + 1);
    token.setUsedAt(Instant.now());
    tokens.revoke(user.getId(), Instant.now());
    audit.eventAs(user, "PASSWORD_RESET", "ACCESS", user.getId(), "Senha redefinida por link");
  }

  @Transactional
  public void accept(AcceptInput input) {
    AuthService.validatePassword(input.password());
    var user = owner(input.token());
    var token = valid(input.token(), AccessToken.Kind.INVITE, true);
    if (!user.isInvited() || user.isActive()) throw invalid();
    user.setName(input.name().strip());
    user.setPasswordHash(encoder.encode(input.password()));
    user.setActive(true);
    user.setInvited(false);
    user.setAuthVersion(user.getAuthVersion() + 1);
    token.setUsedAt(Instant.now());
    tokens.revoke(user.getId(), Instant.now());
    audit.eventAs(user, "INVITE_ACCEPT", "USER", user.getId(), "Convite aceito");
  }

  @Transactional
  public void change(ChangeInput input) {
    AuthService.validatePassword(input.password());
    var user = users.lock(current.get().getId()).orElseThrow();
    em.refresh(user);
    current.get();
    if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
        || !encoder.matches(input.currentPassword(), user.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha atual não confere.");
    if (encoder.matches(input.password(), user.getPasswordHash()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Escolha uma senha diferente da atual.");
    user.setPasswordHash(encoder.encode(input.password()));
    user.setAuthVersion(user.getAuthVersion() + 1);
    tokens.revoke(user.getId(), Instant.now());
    audit.eventAs(user, "PASSWORD_CHANGE", "ACCESS", user.getId(), "Senha alterada");
  }
}
