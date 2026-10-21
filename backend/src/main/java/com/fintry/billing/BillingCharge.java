package com.fintry.billing;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "billing_charges")
@Getter
@Setter
public class BillingCharge extends BaseEntity {
  public enum Method {
    PIX,
    BOLETO
  }

  public enum Status {
    ISSUING,
    PENDING,
    CONFIRMED,
    OVERDUE,
    RECEIVED,
    TEST_RECEIVED,
    CANCELLED,
    REVIEW
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false, unique = true)
  private UUID entryId;

  @Column(nullable = false)
  private UUID connectionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private Method method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(length = 100)
  private String providerId;

  @Column(length = 100)
  private String customerId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate dueDate;

  @Column(length = 80)
  private String providerStatus;

  @Column(length = 2048)
  private String invoiceUrl;

  @Column(length = 2048)
  private String bankSlipUrl;

  @Column(precision = 14, scale = 2)
  private BigDecimal netAmount;

  private LocalDate settledDate;
  private UUID feeEntryId;

  @Column(nullable = false)
  private boolean released;

  @Column(length = 255)
  private String message;
}
