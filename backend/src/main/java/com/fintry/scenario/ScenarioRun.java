package com.fintry.scenario;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scenario_runs")
@Getter
@Setter
public class ScenarioRun extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID scenarioId;

  @Column(nullable = false)
  private UUID requestId;

  @Column(nullable = false)
  private long definitionRevision;

  @Column(nullable = false, length = 120)
  private String author;

  @Column(nullable = false, columnDefinition = "text")
  private String definition;

  @Column(nullable = false, columnDefinition = "text")
  private String result;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false)
  private java.time.Instant calculatedAt;
}
