package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "entry_payments")
@Getter
@Setter
public class EntryPayment extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID entryId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate paymentDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "entry_id", insertable = false, updatable = false)
  private FinancialEntry entry;
}
