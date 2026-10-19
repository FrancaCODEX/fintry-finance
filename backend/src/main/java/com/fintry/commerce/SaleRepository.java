package com.fintry.commerce;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Sale s where s.id=:id and s.companyId=:company")
  Optional<Sale> lock(@Param("id") UUID id, @Param("company") UUID company);

  Optional<Sale> findByIdAndCompanyId(UUID id, UUID companyId);

  Optional<Sale> findByCompanyIdAndRequestId(UUID companyId, UUID requestId);

  @Query("select coalesce(max(s.number),0)+1 from Sale s where s.companyId=:company")
  long nextNumber(@Param("company") UUID company);
}
