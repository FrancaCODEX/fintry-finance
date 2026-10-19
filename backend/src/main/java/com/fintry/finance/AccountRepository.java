package com.fintry.finance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AccountRepository extends JpaRepository<FinancialAccount, UUID> {
    List<FinancialAccount> findByCompanyIdOrderByNameAsc(UUID companyId);
    Optional<FinancialAccount> findByIdAndCompanyId(UUID id, UUID companyId);
}
