package com.fintry.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "access_tokens")
@Getter
@Setter
public class AccessToken {
  public enum Kind {
    RESET,
    INVITE
  }

  @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Kind kind;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;
}
