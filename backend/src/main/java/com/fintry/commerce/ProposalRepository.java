package com.fintry.commerce;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository
    extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {
  Optional<Proposal> findByIdAndCompanyId(UUID id, UUID company);

  Optional<Proposal> findByCompanyIdAndRequestId(UUID company, UUID request);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Proposal p where p.id=:id and p.companyId=:company")
  Optional<Proposal> lock(@Param("id") UUID id, @Param("company") UUID company);

  @Query("select coalesce(max(p.number),0)+1 from Proposal p where p.companyId=:company")
  long nextNumber(@Param("company") UUID company);

  @Query("select p.status,count(p) from Proposal p where p.companyId=:company group by p.status")
  List<Object[]> summary(@Param("company") UUID company);
}
