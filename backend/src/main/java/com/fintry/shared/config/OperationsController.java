package com.fintry.shared.config;

import com.fintry.audit.AuditService;
import com.fintry.auth.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {
  private final CurrentUser current;
  private final Environment env;
  private final DataSource data;
  private final ApplicationAvailability availability;
  private final JavaMailSender sender;
  private final AttemptLimiter limiter;
  private final AuditService audit;
  private final Instant startedAt =
      Instant.ofEpochMilli(
          java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());

  public record Status(
      String version,
      boolean production,
      Instant startedAt,
      boolean ready,
      String database,
      String mailMode,
      boolean secureCookies) {}

  @GetMapping
  public Status status() {
    current.requireAdmin();
    boolean database = false;
    try (var connection = data.getConnection()) {
      database = connection.isValid(2);
    } catch (SQLException ex) {
      /* Return a status, never database/connection details. */
    }
    boolean enabled = env.getProperty("fintry.mail.enabled", Boolean.class, false);
    String host = env.getProperty("spring.mail.host", "").strip().toLowerCase(Locale.ROOT);
    String mode =
        !enabled
            ? "DISABLED"
            : Set.of("mailpit", "localhost", "127.0.0.1").contains(host) ? "LOCAL" : "SMTP";
    return new Status(
        env.getProperty("fintry.version", "development"),
        Arrays.asList(env.getActiveProfiles()).contains("prod"),
        startedAt,
        availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC && database,
        database ? "UP" : "DOWN",
        mode,
        env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false));
  }

  @PostMapping("/mail-test")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> mailTest() {
    current.requireAdmin();
    var user = current.get();
    if (!env.getProperty("fintry.mail.enabled", Boolean.class, false))
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "O envio de e-mail está desabilitado neste ambiente.");
    limiter.take("operations-mail:" + user.getId(), 3, Duration.ofMinutes(10));
    try {
      var message = new SimpleMailMessage();
      message.setFrom(env.getProperty("fintry.mail.from"));
      message.setTo(user.getEmail());
      message.setSubject("Fintry · Teste de e-mail");
      message.setText(
          "Este é um teste de envio solicitado por você no painel Ambiente do Fintry.\n\n"
              + "O teste não altera sua senha nem seus dados financeiros. Se recebeu esta mensagem,"
              + " a entrega chegou à sua caixa de e-mail.");
      sender.send(message);
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "O servidor de e-mail não aceitou o teste. Confira a configuração SMTP no servidor.");
    }
    audit.change(
        "OPERATIONS_MAIL_TEST",
        "OPERATIONS",
        user.getId(),
        "Teste de e-mail",
        Map.of(),
        AuditService.fields("Resultado", "Aceito pelo SMTP", "Destinatário", user.getEmail()));
    return Map.of(
        "message",
        "O servidor SMTP aceitou o envio. Confira sua caixa de entrada e spam para confirmar a"
            + " entrega.");
  }
}
