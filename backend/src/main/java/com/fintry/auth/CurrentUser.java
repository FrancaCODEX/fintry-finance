package com.fintry.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository users;
    public AppUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar.");
        return users.findById(UUID.fromString(auth.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
    }
    public UUID companyId() { return get().getCompanyId(); }
}
