package com.fintry.commerce;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sales")
@Getter
@Setter
public class Sale extends BaseEntity {
  public enum Status {
    DRAFT,
    CONFIRMED,
    CANCELLED
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private long number;

  @Column(nullable = false)
  private UUID requestId;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Status status = Status.DRAFT;

  private UUID contactId;

  @Column(length = 120)
  private String contactName;

  @Column(length = 500)
  private String notes;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total;

  private UUID entryId;
  private Instant confirmedAt;
  private Instant cancelledAt;
}
