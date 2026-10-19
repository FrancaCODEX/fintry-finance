package com.fintry.finance;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SeriesRepository extends JpaRepository<FinancialSeries,UUID> {
    Optional<FinancialSeries> findByCompanyIdAndRequestId(UUID companyId,UUID requestId);
    Page<FinancialSeries> findByCompanyId(UUID companyId,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FinancialSeries s where s.id=:id and s.companyId=:company")
    Optional<FinancialSeries> lock(@Param("id") UUID id,@Param("company") UUID company);
}
