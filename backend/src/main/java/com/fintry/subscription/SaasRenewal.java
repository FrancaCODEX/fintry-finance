package com.fintry.subscription;

import com.fintry.billing.BillingConnection.Environment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saas_renewals")
@Getter
@Setter
public class SaasRenewal {
  @Id private UUID companyId;

  @Column(nullable = false)
  private boolean enabled;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SaasPlan.Code plan;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SaasInvoice.Method method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Environment environment;

  @Column(nullable = false, length = 100)
  private String merchant;

  @Column(nullable = false, length = 100)
  private String customerId;

  @Column(nullable = false)
  private UUID sourceInvoiceId;

  @Column(nullable = false)
  private Instant acceptedAt;

  @Column(nullable = false)
  private UUID acceptedBy;

  @Column(nullable = false)
  private Instant nextCheckAt;

  private Instant lastPeriodEnd;
  private UUID lastInvoiceId;
  @Version private long revision;
}
