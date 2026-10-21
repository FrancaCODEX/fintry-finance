package com.fintry.subscription;

import com.fintry.billing.BillingConnection.Environment;
import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saas_invoices")
@Getter
@Setter
public class SaasInvoice extends BaseEntity {
  public enum Status {
    ISSUING,
    PENDING,
    CONFIRMED,
    OVERDUE,
    PAID,
    TEST_PAID,
    CANCELLING,
    CANCELLED,
    REVIEW
  }

  public enum Method {
    PIX,
    BOLETO
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false, unique = true)
  private UUID requestId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SaasPlan.Code plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Environment environment;

  @Column(nullable = false, length = 100)
  private String merchant;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate dueDate;

  @Column(nullable = false)
  private int periodDays = 30;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Method method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Status status;

  @Column(length = 100, unique = true)
  private String providerId;

  @Column(length = 100)
  private String customerId;

  @Column(length = 2048)
  private String invoiceUrl;

  @Column(length = 255)
  private String message;

  private Instant activeUntil;

  // A cancelled or cancelling invoice can never activate access, even after provider restoration.
  @Column(nullable = false)
  private boolean released;
}
