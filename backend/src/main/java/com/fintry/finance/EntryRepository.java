package com.fintry.finance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface EntryRepository extends JpaRepository<FinancialEntry, UUID>, JpaSpecificationExecutor<FinancialEntry> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from FinancialEntry e where e.id=:id and e.companyId=:company")
    Optional<FinancialEntry> lock(@Param("id") UUID id, @Param("company") UUID companyId);
    @Query("select e from FinancialEntry e where e.companyId=:company and e.status='PAID' and e.paymentDate between :start and :end and e.transferId is null")
    List<FinancialEntry> paidBetween(@Param("company") UUID company, @Param("start") LocalDate start, @Param("end") LocalDate end);
    @Query("select e.accountId, e.type, sum(e.amount) from FinancialEntry e where e.companyId=:company and e.status='PAID' group by e.accountId,e.type")
    List<Object[]> balances(@Param("company") UUID company);
    @Query("select e.type, sum(e.amount) from FinancialEntry e where e.companyId=:company and e.status='PENDING' and e.dueDate between :start and :end group by e.type")
    List<Object[]> pending(@Param("company") UUID company, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
