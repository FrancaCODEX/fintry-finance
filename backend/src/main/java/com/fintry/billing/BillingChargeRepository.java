package com.fintry.billing;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingChargeRepository extends JpaRepository<BillingCharge, UUID> {
  Optional<BillingCharge> findByEntryId(UUID entryId);

  Optional<BillingCharge> findByIdAndCompanyId(UUID id, UUID companyId);

  Optional<BillingCharge> findByConnectionIdAndProviderId(UUID connectionId, String providerId);

  Page<BillingCharge> findByCompanyId(UUID companyId, Pageable pageable);

  boolean existsByCompanyId(UUID companyId);

  boolean existsByConnectionId(UUID connectionId);

  boolean existsByFeeEntryId(UUID feeEntryId);
}
