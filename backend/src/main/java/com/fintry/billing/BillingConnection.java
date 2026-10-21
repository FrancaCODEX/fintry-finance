package com.fintry.billing;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "billing_connections")
@Getter
@Setter
public class BillingConnection extends BaseEntity {
  public enum Environment {
    SANDBOX,
    PRODUCTION
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private boolean active = true;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Environment environment;

  @Column(nullable = false, length = 4096)
  private String apiKey;

  @Column(nullable = false, length = 4096)
  private String webhookToken;
}
