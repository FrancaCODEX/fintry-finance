package com.fintry.commerce;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface StockRepository
    extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {
  Optional<StockMovement> findByCompanyIdAndRequestId(UUID companyId, UUID requestId);
}
