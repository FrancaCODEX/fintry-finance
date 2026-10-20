package com.fintry.budget;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "category_budgets")
@Getter
@Setter
public class CategoryBudget extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID categoryId;

  @Column(name = "period_start", nullable = false)
  private LocalDate month;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal limitAmount;
}
