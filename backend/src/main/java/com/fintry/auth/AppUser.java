package com.fintry.auth;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_users")
@Getter
@Setter
public class AppUser extends BaseEntity {
  public enum Role {
    ADMIN,
    FINANCE,
    VIEWER
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Role role = Role.ADMIN;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private boolean invited = false;

  @Column(nullable = false)
  private long authVersion = 0;

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;
}
