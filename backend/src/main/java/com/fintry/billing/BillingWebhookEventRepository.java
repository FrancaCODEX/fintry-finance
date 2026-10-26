package com.fintry.billing;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BillingWebhookEventRepository
    extends JpaRepository<BillingWebhookEvent, UUID> {
  boolean existsByConnectionIdAndProviderEventId(UUID connectionId, String providerEventId);

  List<BillingWebhookEvent> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
      BillingWebhookEvent.Status status, LocalDateTime due);

  List<BillingWebhookEvent> findTop50ByOrderByCreatedAtDesc();

  List<BillingWebhookEvent> findTop50ByStatusAndUpdatedAtBefore(
      BillingWebhookEvent.Status status, LocalDateTime before);

  long countByStatus(BillingWebhookEvent.Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from BillingWebhookEvent e where e.id = :id")
  Optional<BillingWebhookEvent> lockById(@Param("id") UUID id);
}
