package com.fintry.scenario;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRunRepository extends JpaRepository<ScenarioRun, UUID> {
  Optional<ScenarioRun> findByIdAndScenarioIdAndCompanyId(UUID id, UUID scenario, UUID company);

  Optional<ScenarioRun> findByScenarioIdAndCompanyIdAndRequestId(
      UUID id, UUID company, UUID request);

  Optional<ScenarioRun> findFirstByScenarioIdAndCompanyIdOrderByCreatedAtDescIdDesc(
      UUID id, UUID company);

  Page<ScenarioRun> findByScenarioIdAndCompanyIdOrderByCreatedAtDescIdDesc(
      UUID id, UUID company, Pageable page);
}
