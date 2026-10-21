package com.fintry.finance;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementSourceRepository extends JpaRepository<StatementSource, UUID> {
  Optional<StatementSource> findByCompanyIdAndSourceHash(UUID companyId, String sourceHash);
}
