package com.fintry.subscription;

import java.util.*;

public interface PlanRequestRepository
    extends org.springframework.data.jpa.repository.JpaRepository<PlanRequest, UUID> {
  Optional<PlanRequest> findByIdAndCompanyId(UUID id, UUID companyId);

  List<PlanRequest> findTop10ByCompanyIdOrderByCreatedAtDescIdDesc(UUID companyId);
}
