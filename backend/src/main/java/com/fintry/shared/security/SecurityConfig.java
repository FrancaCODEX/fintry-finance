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
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.securityContext(c -> c.securityContextRepository(new HttpSessionSecurityContextRepository()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/app.js", "/theme.js", "/style.css", "/favicon.svg",
                    "/api/v1/app", "/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/register", "/actuator/health", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"Entre na sua conta para continuar.\"}");
                })
                .accessDeniedHandler((req,res,ex) -> {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"Sessão de segurança expirada. Recarregue a página.\"}");
                }))
            .logout(l -> l.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler((req,res,a) -> res.setStatus(204)))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }
}
