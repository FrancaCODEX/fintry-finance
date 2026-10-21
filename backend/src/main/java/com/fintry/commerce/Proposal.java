package com.fintry.commerce;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proposals")
@Getter
@Setter
public class Proposal extends BaseEntity {
  public enum Status {
    DRAFT,
    OPEN,
    APPROVED,
    REJECTED,
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

  @Column(nullable = false)
  private long version;

  @Column(nullable = false, length = 120)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Status status = Status.DRAFT;

  private UUID contactId;

  @Column(length = 120)
  private String contactName;

  @Column(length = 500)
  private String notes;

  @Column(nullable = false)
  private LocalDate validUntil;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total;

  private UUID saleId;
}
