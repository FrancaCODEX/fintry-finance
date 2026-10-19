package com.fintry.commerce;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends BaseEntity {
  public enum Kind {
    INITIAL,
    IN,
    OUT,
    SALE,
    RETURN
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private UUID productId;

  private UUID saleId;
  private UUID requestId;

  @Column(length = 64)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Kind kind;

  @Column(nullable = false, length = 120)
  private String productName;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 8)
  private String unit;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal delta;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal balance;

  @Column(nullable = false, length = 255)
  private String reason;

  @Column(nullable = false, length = 120)
  private String actorName;
}
