package com.fintry.shared.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionGuardTests {
  MockEnvironment valid() {
    var e = new MockEnvironment();
    e.setActiveProfiles("prod");
    return e.withProperty("fintry.base-url", "https://fintry.integration.test")
        .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/fintry")
        .withProperty("spring.datasource.password", "fixture-secret-123456789012345")
        .withProperty("server.servlet.session.cookie.secure", "true")
        .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
        .withProperty("fintry.mail.enabled", "true")
        .withProperty("spring.mail.host", "relay")
        .withProperty("fintry.mail.from", "noreply@fintry.integration.test")
        .withProperty("spring.mail.username", "fixture")
        .withProperty("spring.mail.password", "fixture-mail-secret")
        .withProperty("spring.mail.properties.mail.smtp.auth", "true")
        .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
        .withProperty("spring.mail.properties.mail.smtp.starttls.required", "true")
        .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "false")
        .withProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity", "true");
  }

  @Test
  void acceptsAuthenticatedStarttlsOrImplicitTlsWithoutRelaxingCertificateChecks() {
    assertDoesNotThrow(() -> ProductionGuard.validate(valid()));
    assertDoesNotThrow(
        () ->
            ProductionGuard.validate(
                valid()
                    .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "false")
                    .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "true")));
  }

  @Test
  void rejectsDevelopmentDefaultsMixedProfilesAndInsecureCredentialsWithoutEchoingSecrets() {
    var values =
        new String[][] {
          {"fintry.base-url", "http://localhost:8080"},
          {"fintry.base-url", "https://user:secret@host.invalid"},
          {"fintry.base-url", "https://host.invalid/path"},
          {"spring.datasource.url", "jdbc:h2:mem:test"},
          {"spring.datasource.password", "fintry-local"},
          {"server.servlet.session.cookie.secure", "false"},
          {"spring.jpa.hibernate.ddl-auto", "update"},
          {"spring.flyway.enabled", "false"},
          {"fintry.mail.enabled", "false"},
          {"spring.mail.host", "mailpit"},
          {"spring.mail.properties.mail.smtp.auth", "false"},
          {"spring.mail.password", ""},
          {"spring.mail.properties.mail.smtp.starttls.required", "false"},
          {"spring.mail.properties.mail.smtp.ssl.enable", "true"},
          {"spring.mail.properties.mail.smtp.ssl.checkserveridentity", "false"}
        };
    for (var pair : values) {
      var error =
          assertThrows(
              IllegalStateException.class,
              () -> ProductionGuard.validate(valid().withProperty(pair[0], pair[1])),
              pair[0]);
      assertFalse(error.getMessage().contains("fixture-secret"));
      assertFalse(error.getMessage().contains("fixture-mail-secret"));
      assertFalse(error.getMessage().contains("user:secret"));
    }
    var env = valid();
    env.setActiveProfiles("prod", "demo");
    assertThrows(IllegalStateException.class, () -> ProductionGuard.validate(env));
  }
}
