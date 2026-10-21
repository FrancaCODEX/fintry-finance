package com.fintry.subscription;

import com.fintry.billing.BillingConnection.Environment;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fintry.saas.payments")
@Getter
@Setter
public class SaasPaymentsConfig {
  private boolean enabled;
  private Environment environment = Environment.SANDBOX;
  private String account = "";
  private String apiKey = "";
  private String webhookToken = "";
  private BigDecimal essential, pro, business;

  @PostConstruct
  void validate() {
    for (var code : SaasPlan.Code.values()) {
      var price = price(code);
      if (price != null
          && (price.scale() > 2
              || price.compareTo(new BigDecimal("5.00")) < 0
              || price.compareTo(new BigDecimal("9999999999.99")) > 0))
        throw new IllegalStateException(
            "Preço SaaS inválido: informe reais, duas casas e valor mínimo de 5.");
    }
    if (enabled
        && (environment == null
            || !account.matches("[A-Za-z0-9_-]{3,100}")
            || apiKey.isBlank()
            || apiKey.length() > 2000
            || !webhookToken.matches("[A-Za-z0-9_-]{32,200}")
            || apiKey.equals(webhookToken)))
      throw new IllegalStateException(
          "Configure a conta recebedora e as credenciais próprias das mensalidades SaaS.");
  }

  public BigDecimal price(SaasPlan.Code code) {
    return switch (code) {
      case ESSENTIAL -> essential;
      case PRO -> pro;
      case BUSINESS -> business;
    };
  }
}
