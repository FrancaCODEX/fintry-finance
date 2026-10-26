package com.fintry.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      com.fintry.auth.UserRepository users,
      com.fintry.subscription.SubscriptionService subscriptions)
      throws Exception {
    http.securityContext(
            c -> c.securityContextRepository(new HttpSessionSecurityContextRepository()))
        .csrf(
            c ->
                c.ignoringRequestMatchers(
                    org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                        .withDefaults()
                        .matcher(org.springframework.http.HttpMethod.POST, "/webhooks/asaas/{id}"),
                    org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                        .withDefaults()
                        .matcher(
                            org.springframework.http.HttpMethod.POST,
                            "/webhooks/fintry-subscription/asaas")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/webhooks/asaas/{id}",
                        "/webhooks/fintry-subscription/asaas")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/index.html",
                        "/app.js",
                        "/commerce.js",
                        "/proposals.js",
                        "/ux.js",
                        "/pending.js",
                        "/budgets.js",
                        "/imports.js",
                        "/operations.js",
                        "/billing.js",
                        "/subscription.js",
                        "/reconciliation.js",
                        "/theme.js",
                        "/style.css",
                        "/design.css",
                        "/navigation.js",
                        "/dashboard.js",
                        "/company.js",
                        "/favicon.svg",
                        "/api/v1/app",
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/invitation",
                        "/api/v1/auth/accept-invite",
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/users",
                        "/api/v1/users/**",
                        "/api/v1/audit",
                        "/api/v1/operations",
                        "/api/v1/operations/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/subscription/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.PUT, "/api/v1/billing/connection")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/auth/change-password")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.PUT, "/api/v1/companies/**")
                    .hasRole("ADMIN")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/**")
                    .hasAnyRole("ADMIN", "FINANCE")
                    .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/**")
                    .hasAnyRole("ADMIN", "FINANCE")
                    .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/**")
                    .hasAnyRole("ADMIN", "FINANCE")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new com.fintry.auth.SessionAccessFilter(users),
            org.springframework.security.web.access.intercept.AuthorizationFilter.class)
        .addFilterAfter(
            new com.fintry.subscription.SubscriptionAccessFilter(subscriptions),
            org.springframework.security.web.access.intercept.AuthorizationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write("{\"message\":\"Entre na sua conta para continuar.\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  ex instanceof org.springframework.security.web.csrf.CsrfException
                                      ? "{\"message\":\"Sessão de segurança expirada. Recarregue a"
                                          + " página.\"}"
                                      : "{\"message\":\"Seu perfil não permite esta ação.\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler((req, res, a) -> res.setStatus(204)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                    + " 'self' data:; connect-src 'self'; frame-ancestors 'none';"
                                    + " base-uri 'self'; form-action 'self'"))
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
    return http.build();
  }
}
