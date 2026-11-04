package com.fintry.scenario;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedScenarioRepository extends JpaRepository<SavedScenario, UUID> {
  Optional<SavedScenario> findByIdAndCompanyId(UUID id, UUID company);

  Optional<SavedScenario> findByCompanyIdAndCreationKey(UUID company, UUID key);

  Page<SavedScenario> findByCompanyIdAndArchivedOrderByUpdatedAtDescIdDesc(
      UUID company, boolean archived, Pageable page);
}
