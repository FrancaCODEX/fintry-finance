package com.fintry.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryPaymentRepository
    extends JpaRepository<EntryPayment, UUID>, JpaSpecificationExecutor<EntryPayment> {
  List<EntryPayment> findByEntryIdOrderByPaymentDateAscCreatedAtAscIdAsc(UUID entryId);

  @Query("select coalesce(sum(p.amount),0) from EntryPayment p where p.entryId=:entry")
  BigDecimal totalForEntry(@Param("entry") UUID entryId);

  @Query(
      "select e.accountId,e.type,sum(p.amount) from EntryPayment p join p.entry e"
          + " where p.companyId=:company and e.status<>'CANCELLED'"
          + " group by e.accountId,e.type")
  List<Object[]> balances(@Param("company") UUID companyId);

  @Query(
      "select e.type,p.amount,p.paymentDate,e.category from EntryPayment p join p.entry e"
          + " where p.companyId=:company and p.paymentDate between :start and :end"
          + " and e.transferId is null and e.status<>'CANCELLED'")
  List<Object[]> settledBetween(
      @Param("company") UUID companyId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}
