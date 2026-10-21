package com.fintry.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository
    extends JpaRepository<AppUser, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<AppUser> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
  Optional<AppUser> lock(@org.springframework.data.repository.query.Param("id") UUID id);

  Optional<AppUser> findByIdAndCompanyId(UUID id, UUID companyId);

  java.util.List<AppUser> findByCompanyIdOrderByNameAsc(UUID companyId);

  long countByCompanyIdAndActive(UUID companyId, boolean active);

  long countByCompanyIdAndInvited(UUID companyId, boolean invited);

  long countByCompanyIdAndRoleAndActive(UUID companyId, AppUser.Role role, boolean active);

  Optional<AppUser> findByEmail(String email);

  @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.companyId=:company and (u.active=true or u.invited=true)")
  long subscriptionSeats(@org.springframework.data.repository.query.Param("company") UUID company);

  boolean existsByEmail(String email);
}
