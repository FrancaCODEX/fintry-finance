package com.fintry.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAccessFilter extends OncePerRequestFilter {
  private final UserRepository users;

  public SessionAccessFilter(UserRepository users) {
    this.users = users;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Probes must keep working during a database outage, even with an old session cookie.
    return request.getMethod().equals("GET")
        && java.util.Set.of(
                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
            .contains(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var context = SecurityContextHolder.getContext();
    var auth = context.getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof AuthSession session) {
      var user = users.findById(session.id());
      if (user.isEmpty()
          || !user.get().isActive()
          || user.get().isInvited()
          || user.get().getAuthVersion() != session.version()) {
        SecurityContextHolder.clearContext();
        if (req.getSession(false) != null) req.getSession(false).invalidate();
      } else
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                session,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name()))));
    } else if (auth != null
        && auth.isAuthenticated()
        && !(auth.getPrincipal() instanceof String s && s.equals("anonymousUser"))) {
      SecurityContextHolder.clearContext();
      if (req.getSession(false) != null) req.getSession(false).invalidate();
    }
    chain.doFilter(req, res);
  }
}
