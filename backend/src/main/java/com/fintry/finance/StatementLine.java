package com.fintry.finance;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "statement_lines")
@Getter
@Setter
public class StatementLine extends BaseEntity {
  public enum Status {
    OPEN,
    LINKED,
    IGNORED
  }

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private UUID sourceId;

  @Column(nullable = false, length = 255)
  private String fitId;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private LocalDate postedDate;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Status status;

  private UUID entryId;

  @Column(length = 255)
  private String reason;

  @Column(nullable = false)
  private long revision;
}
