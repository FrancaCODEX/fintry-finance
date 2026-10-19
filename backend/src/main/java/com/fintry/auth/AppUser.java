package com.fintry.auth;

import com.fintry.shared.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter @Setter
public class AppUser extends BaseEntity {
    @Column(nullable = false) private UUID companyId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String passwordHash;
}
