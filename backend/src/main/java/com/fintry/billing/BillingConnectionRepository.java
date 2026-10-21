package com.fintry.billing;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingConnectionRepository extends JpaRepository<BillingConnection, UUID> {
  Optional<BillingConnection> findByCompanyIdAndActiveTrue(UUID companyId);
}
