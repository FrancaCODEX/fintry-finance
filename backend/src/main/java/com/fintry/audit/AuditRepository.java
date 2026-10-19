package com.fintry.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface AuditRepository
    extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
