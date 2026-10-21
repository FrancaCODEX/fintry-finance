package com.fintry.subscription;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "saas_plans")
@Getter
@Setter
public class SaasPlan {
  public enum Code {
    ESSENTIAL,
    PRO,
    BUSINESS
  }

  @Id
  @Enumerated(EnumType.STRING)
  @Column(length = 12)
  private Code code;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private int memberLimit;

  @Column(nullable = false)
  private int accountLimit;

  @Column(nullable = false)
  private long storageBytes;

  @Column(precision = 12, scale = 2)
  private BigDecimal monthlyPrice;
}
