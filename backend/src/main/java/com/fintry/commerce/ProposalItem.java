package com.fintry.commerce;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proposal_items")
@Getter
@Setter
public class ProposalItem extends BaseEntity {
  @Column(nullable = false)
  private UUID proposalId;

  @Column(nullable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Product.Kind kind;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 120)
  private String productName;

  @Column(nullable = false, length = 8)
  private String unit;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total;
}
