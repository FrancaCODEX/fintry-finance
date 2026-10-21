package com.fintry.subscription;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class SubscriptionAccessFilter extends OncePerRequestFilter {
  private final SubscriptionService service;

  public SubscriptionAccessFilter(SubscriptionService service) {
    this.service = service;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    if (java.util.Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod())) return true;
    String p = r.getServletPath();
    // Security, membership administration and subscription requests remain available.
    // Downloads/exports and removal of attachments remain available even after expiration.
    if (!p.startsWith("/api/v1/")
        || p.startsWith("/api/v1/auth/")
        || p.equals("/api/v1/users")
        || p.startsWith("/api/v1/users/")
        || p.startsWith("/api/v1/subscription")
        || p.startsWith("/api/v1/operations")
        || p.startsWith("/api/v1/companies/")) return true;
    if (p.matches("/api/v1/billing/[^/]+/(refresh|cancel|pix)")) return true;
    return r.getMethod().equals("DELETE")
        && p.matches("/api/v1/finance/entries/[^/]+/attachments/[^/]+");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest r, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!service.writable()) {
      response.setStatus(402);
      response.setContentType("application/json;charset=UTF-8");
      response
          .getWriter()
          .write(
              "{\"message\":\"Seu período terminou. Consulte e exporte seus dados e escolha um"
                  + " plano em Plano e assinatura.\"}");
      return;
    }
    chain.doFilter(r, response);
  }
}
