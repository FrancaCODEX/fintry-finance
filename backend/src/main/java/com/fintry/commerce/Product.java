package com.fintry.commerce;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 8)
  private String unit;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal cost;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal minimum;

  @Column(nullable = false)
  private boolean active = true;
}
