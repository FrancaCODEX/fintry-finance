package com.fintry.sample;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sample_runs")
@Getter
@Setter
public class SampleRun {
  @Id private UUID companyId;

  @Column(nullable = false)
  private LocalDateTime completedAt;

  @Column(nullable = false)
  private int packVersion;
}
