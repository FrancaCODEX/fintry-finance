package com.fintry.auth;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CurrentUser {
  private final UserRepository users;

  public AppUser get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar.");
    var user =
        users
            .findById(UUID.fromString(auth.getName()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
    if (!user.isActive()
        || user.isInvited()
        || !(auth.getPrincipal() instanceof AuthSession session)
        || session.version() != user.getAuthVersion())
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Seu acesso mudou. Entre novamente.");
    return user;
  }

  public void requireAdmin() {
    if (get().getRole() != AppUser.Role.ADMIN)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Esta ação exige o perfil Administrador.");
  }

  public UUID companyId() {
    return get().getCompanyId();
  }
}
