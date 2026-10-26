package com.fintry.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, UUID> {
  Optional<ReconciliationBatch> findByIdAndCompanyId(UUID id, UUID companyId);
}
