package com.fintry.directory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CategoryRepository extends JpaRepository<FinancialCategory,UUID> {
    List<FinancialCategory> findByCompanyIdOrderByNameAsc(UUID companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FinancialCategory c where c.id=:id and c.companyId=:company")
    Optional<FinancialCategory> lock(@Param("id") UUID id,@Param("company") UUID company);
}
