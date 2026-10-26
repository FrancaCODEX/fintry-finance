package com.fintry.finance;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface StatementLineRepository
    extends JpaRepository<StatementLine, UUID>, JpaSpecificationExecutor<StatementLine> {
  Optional<StatementLine> findByIdAndCompanyId(UUID id, UUID companyId);

  Optional<StatementLine> findBySourceIdAndFitId(UUID sourceId, String fitId);

  boolean existsByEntryId(UUID entryId);

  long countByCompanyIdAndAccountIdAndStatus(
      UUID companyId, UUID accountId, StatementLine.Status status);

  long countByReconciliationBatchId(UUID batchId);
}
