package com.fintry.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    private final CurrentUser current;
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody AuthService.RegisterRequest input) { service.register(input); }
    @PostMapping("/login")
    public AuthService.UserView login(@Valid @RequestBody AuthService.LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        var user = service.authenticate(input);
        request.getSession(true); request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            user.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return service.view(user);
    }
    @GetMapping("/me")
    public AuthService.UserView me() { return service.view(current.get()); }
}
