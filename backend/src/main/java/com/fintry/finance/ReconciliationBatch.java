package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reconciliation_batches")
@Getter
@Setter
public class ReconciliationBatch extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private UUID entryId;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate paymentDate;

  @Column(nullable = false, length = 255)
  private String reason;
}
