package com.fintry.shared.config;

import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail before serving a production instance that still uses development defaults. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionGuard implements ApplicationRunner {
  private final Environment env;

  @Override
  public void run(ApplicationArguments args) {
    validate(env);
  }

  static void validate(Environment env) {
    var errors = new ArrayList<String>();
    if (Arrays.stream(env.getActiveProfiles())
        .anyMatch(Set.of("demo", "dev", "test", "postgres-test")::contains))
      errors.add("use somente o perfil prod para publicação");
    String url = env.getProperty("fintry.base-url", "");
    try {
      var uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getHost().equalsIgnoreCase("localhost")
          || uri.getHost().equals("127.0.0.1")
          || uri.getHost().equals("[::1]")
          || !uri.getHost().contains(".")
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
        errors.add(
            "APP_BASE_URL deve usar HTTPS e domínio, sem credenciais, caminho, query ou fragmento");
    } catch (IllegalArgumentException ex) {
      errors.add("APP_BASE_URL inválida");
    }
    if (!env.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql://"))
      errors.add("configure PostgreSQL em DB_URL");
    String password = env.getProperty("spring.datasource.password", "");
    if (password.length() < 20
        || password.toLowerCase(Locale.ROOT).contains("change_me")
        || password.equals("fintry-local"))
      errors.add("DB_PASSWORD deve ter pelo menos 20 caracteres e não usar uma senha de exemplo");
    if (!Boolean.parseBoolean(env.getProperty("server.servlet.session.cookie.secure", "false")))
      errors.add("cookies Secure devem estar habilitados");
    if (!Boolean.parseBoolean(env.getProperty("spring.flyway.enabled", "true"))
        || !env.getProperty("spring.jpa.hibernate.ddl-auto", "").equals("validate"))
      errors.add("preserve Flyway habilitado e Hibernate em validate");
    boolean enabled = Boolean.parseBoolean(env.getProperty("fintry.mail.enabled", "false"));
    String host = env.getProperty("spring.mail.host", "").strip().toLowerCase(Locale.ROOT);
    String from = env.getProperty("fintry.mail.from", "");
    if (!enabled
        || host.isBlank()
        || Set.of("localhost", "127.0.0.1", "::1", "mailpit").contains(host)
        || host.endsWith(".local")
        || !from.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")
        || from.endsWith(".local"))
      errors.add("configure e-mail habilitado, SMTP real e remetente em MAIL_FROM");
    if (!Boolean.parseBoolean(env.getProperty("spring.mail.properties.mail.smtp.auth", "false"))
        || env.getProperty("spring.mail.username", "").isBlank()
        || env.getProperty("spring.mail.password", "").isBlank())
      errors.add("configure autenticação SMTP, usuário e senha");
    boolean tls =
        Boolean.parseBoolean(
            env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));
    boolean ssl =
        Boolean.parseBoolean(
            env.getProperty("spring.mail.properties.mail.smtp.ssl.enable", "false"));
    if (tls == ssl
        || tls
            && !Boolean.parseBoolean(
                env.getProperty("spring.mail.properties.mail.smtp.starttls.required", "false"))
        || !Boolean.parseBoolean(
            env.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity", "false")))
      errors.add("SMTP exige STARTTLS obrigatório ou TLS implícito, com validação do certificado");
    if (!errors.isEmpty())
      throw new IllegalStateException(
          "Produção não configurada: "
              + String.join("; ", errors)
              + ". Valores e credenciais foram omitidos.");
  }
}
