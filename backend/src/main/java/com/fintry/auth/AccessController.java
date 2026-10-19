package com.fintry.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AccessController {
  private final AccessFlows flows;
  private final AttemptLimiter limiter;

  private void endSession(HttpServletRequest request) {
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> forgot(
      @Valid @RequestBody AccessFlows.EmailInput input, HttpServletRequest request) {
    limiter.take("forgot-ip:" + request.getRemoteAddr(), 30, Duration.ofMinutes(15));
    limiter.take(
        "forgot-email:" + input.email().strip().toLowerCase(Locale.ROOT),
        3,
        Duration.ofMinutes(15));
    flows.forgot(input.email());
    return Map.of(
        "message",
        "Se houver uma conta ativa com esse e-mail, enviaremos um link para redefinir a senha.");
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Valid @RequestBody AccessFlows.ResetInput input, HttpServletRequest request) {
    limiter.take("token-ip:" + request.getRemoteAddr(), 60, Duration.ofMinutes(15));
    flows.reset(input);
    endSession(request);
  }

  @PostMapping("/invitation")
  public AccessFlows.InvitationInfo inspect(
      @Valid @RequestBody AccessFlows.TokenInput input, HttpServletRequest request) {
    limiter.take("inspect-ip:" + request.getRemoteAddr(), 100, Duration.ofMinutes(15));
    return flows.inspect(input.token());
  }

  @PostMapping("/accept-invite")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void accept(
      @Valid @RequestBody AccessFlows.AcceptInput input, HttpServletRequest request) {
    limiter.take("token-ip:" + request.getRemoteAddr(), 60, Duration.ofMinutes(15));
    flows.accept(input);
    endSession(request);
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void change(
      @Valid @RequestBody AccessFlows.ChangeInput input, HttpServletRequest request) {
    flows.change(input);
    endSession(request);
  }
}
