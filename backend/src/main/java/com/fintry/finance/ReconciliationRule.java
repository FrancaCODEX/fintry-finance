package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reconciliation_rules")
@Getter
@Setter
public class ReconciliationRule extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 120)
  private String author;

  @Column(nullable = false, length = 80)
  private String bankText;

  @Column(nullable = false, length = 80)
  private String entryText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private FinancialEntry.Type direction;

  @Column(nullable = false)
  private int windowDays;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private UUID creationKey;

  @Column(nullable = false, length = 64)
  private String creationHash;

  @Version
  @Column(nullable = false)
  private Long revision;
}
