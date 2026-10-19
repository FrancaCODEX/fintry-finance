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
  SecurityFilterChain securityFilterChain(HttpSecurity http, com.fintry.auth.UserRepository users)
      throws Exception {
    http.securityContext(
            c -> c.securityContextRepository(new HttpSessionSecurityContextRepository()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/app.js",
                        "/theme.js",
                        "/style.css",
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
                        "/error")
                    .permitAll()
                    .requestMatchers("/api/v1/users", "/api/v1/users/**", "/api/v1/audit")
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
