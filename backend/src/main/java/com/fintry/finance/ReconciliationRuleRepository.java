package com.fintry.finance;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReconciliationRuleRepository
    extends JpaRepository<ReconciliationRule, UUID>, JpaSpecificationExecutor<ReconciliationRule> {
  Optional<ReconciliationRule> findByIdAndCompanyId(UUID id, UUID company);

  Optional<ReconciliationRule> findByCompanyIdAndCreationKey(UUID company, UUID request);

  List<ReconciliationRule> findByCompanyIdAndAccountIdAndActiveTrueOrderByNameAscIdAsc(
      UUID company, UUID account);
}
