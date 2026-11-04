package com.fintry.scenario;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saved_scenarios")
@Getter
@Setter
public class SavedScenario extends BaseEntity {
  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID createdBy;

  @Column(nullable = false, length = 120)
  private String author;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, columnDefinition = "text")
  private String definition;

  @Column(nullable = false)
  private UUID creationKey;

  @Column(nullable = false, length = 64)
  private String creationHash;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Column(nullable = false)
  private boolean archived;

  @Column(nullable = false)
  private java.time.Instant modifiedAt;
}
