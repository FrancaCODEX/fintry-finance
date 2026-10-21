package com.fintry.subscription;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "saas_plan_requests")
@Getter
@Setter
public class PlanRequest extends BaseEntity {
  public enum Status {
    PENDING,
    APPROVED,
    CANCELLED
  }

  @Column(nullable = false)
  private UUID companyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SaasPlan.Code plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Status status;

  @Column(length = 120)
  private String reference;

  private Integer grantedDays;
  private Instant activeUntil;
}
