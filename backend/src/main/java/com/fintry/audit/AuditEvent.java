package com.fintry.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {
  @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator private UUID id;

  @Column(nullable = false)
  private UUID companyId;

  private UUID actorId;

  @Column(nullable = false, length = 120)
  private String actorName;

  @Column(nullable = false, length = 32)
  private String action;

  @Column(nullable = false, length = 24)
  private String resource;

  private UUID resourceId;

  @Column(nullable = false, length = 255)
  private String label;

  @Column(nullable = false, columnDefinition = "text")
  private String changesJson;

  @Column(nullable = false)
  private Instant occurredAt;
}
