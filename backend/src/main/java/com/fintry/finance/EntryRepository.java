package com.fintry.finance;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EntryRepository
    extends JpaRepository<FinancialEntry, UUID>, JpaSpecificationExecutor<FinancialEntry> {
  @Query(
      "select count(e) from FinancialEntry e where e.companyId=:company and e.accountId=:account"
          + " and lower(trim(e.description))=:description and e.type=:type and e.amount=:amount and"
          + " e.dueDate=:date and e.status<>'CANCELLED' and e.transferId is null")
  long importDuplicate(
      @Param("company") UUID company,
      @Param("account") UUID account,
      @Param("description") String description,
      @Param("type") FinancialEntry.Type type,
      @Param("amount") java.math.BigDecimal amount,
      @Param("date") LocalDate date);

  Optional<FinancialEntry> findByIdAndCompanyId(UUID id, UUID companyId);

  @Query(
      "select e.dueDate,e.type,sum(e.amount-e.paidAmount) from FinancialEntry e where"
          + " e.companyId=:company and e.status in ('PENDING','PARTIAL')"
          + " and e.transferId is null and e.dueDate<=:end and (:account is null"
          + " or e.accountId=:account) group by e.dueDate,e.type")
  List<Object[]> forecast(
      @Param("company") UUID company, @Param("account") UUID account, @Param("end") LocalDate end);

  List<FinancialEntry> findByCompanyIdAndSeriesIdIn(UUID companyId, List<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from FinancialEntry e where e.seriesId=:id and e.companyId=:company order by"
          + " e.sequenceNumber")
  List<FinancialEntry> lockSeries(@Param("id") UUID id, @Param("company") UUID company);

  boolean existsByContactIdAndCompanyId(UUID contactId, UUID companyId);

  boolean existsByCategoryIdAndCompanyId(UUID categoryId, UUID companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from FinancialEntry e where e.id=:id and e.companyId=:company")
  Optional<FinancialEntry> lock(@Param("id") UUID id, @Param("company") UUID companyId);

  @Query(
      "select e.type, sum(e.amount-e.paidAmount) from FinancialEntry e where e.companyId=:company"
          + " and e.status in ('PENDING','PARTIAL') and e.dueDate between :start and :end"
          + " group by e.type")
  List<Object[]> pending(
      @Param("company") UUID company, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
