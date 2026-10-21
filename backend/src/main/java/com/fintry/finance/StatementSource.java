package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "statement_sources")
@Getter
@Setter
public class StatementSource extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, length = 64)
  private String sourceHash;

  @Column(nullable = false, length = 40)
  private String bank;

  @Column(nullable = false, length = 8)
  private String accountHint;
}
