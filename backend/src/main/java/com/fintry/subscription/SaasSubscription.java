package com.fintry.subscription;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.*;

@Entity
@Table(name = "saas_subscriptions")
@Getter
@Setter
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
public class SaasSubscription {
  public enum State {
    TRIAL,
    ACTIVE,
    LEGACY,
    SIMULATED
  }

  @jakarta.persistence.Id private UUID companyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SaasPlan.Code plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private State state;

  @Column(nullable = false)
  private Instant trialEndsAt;

  private Instant paidUntil;

  @Column(nullable = false)
  private boolean cancelAtPeriodEnd;

  private UUID pendingRequestId;
  @jakarta.persistence.Version private long revision;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
