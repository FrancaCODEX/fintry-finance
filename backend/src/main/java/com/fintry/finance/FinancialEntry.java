package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "financial_entries")
@Getter
@Setter
public class FinancialEntry extends BaseEntity {
  public enum Type {
    INCOME,
    EXPENSE
  }

  public enum Status {
    PENDING,
    PAID,
    CANCELLED
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false, length = 80)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Type type;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate dueDate;

  private LocalDate paymentDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Status status;

  private UUID transferId;
  private UUID saleId;
  private UUID seriesId;
  private Integer sequenceNumber;
  private Integer seriesCount;

  @Enumerated(EnumType.STRING)
  @Column(length = 12)
  private FinancialSeries.Kind seriesKind;

  private UUID categoryId;
  private UUID contactId;

  @Column(length = 120)
  private String contactName;
}
