package com.fintry.subscription;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface SaasRenewalRepository extends JpaRepository<SaasRenewal, UUID> {
  @Query(
      "select r.companyId from SaasRenewal r where r.enabled=true and r.nextCheckAt<=:now order by"
          + " r.nextCheckAt,r.companyId")
  List<UUID> due(Instant now, Pageable page);
}
