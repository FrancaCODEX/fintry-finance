package com.fintry.billing;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "billing_webhook_events",
    uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "provider_event_id"}))
@Getter
@Setter
public class BillingWebhookEvent extends BaseEntity {
  public enum Status {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
  }

  @Column(nullable = false)
  private UUID connectionId;

  @Column(nullable = false, length = 100)
  private String providerEventId;

  @Column(nullable = false, length = 100)
  private String providerPaymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private LocalDateTime nextAttemptAt;

  private LocalDateTime processedAt;

  @Column(length = 160)
  private String lastError;
}
