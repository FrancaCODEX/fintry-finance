package com.fintry.subscription;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaasInvoiceRepository extends JpaRepository<SaasInvoice, UUID> {
  Optional<SaasInvoice> findByRequestId(UUID request);

  Optional<SaasInvoice> findByIdAndCompanyId(UUID id, UUID company);

  Optional<SaasInvoice> findByProviderId(String id);

  List<SaasInvoice> findTop20ByCompanyIdOrderByCreatedAtDescIdDesc(UUID company);
}
