package com.fintry.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {
  @Query("select t.userId from AccessToken t where t.tokenHash=:hash")
  Optional<UUID> owner(@Param("hash") String hash);

  Optional<AccessToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from AccessToken t where t.tokenHash=:hash")
  Optional<AccessToken> lock(@Param("hash") String hash);

  @Modifying(flushAutomatically = true)
  @Query("update AccessToken t set t.usedAt=:now where t.userId=:user and t.usedAt is null")
  int revoke(@Param("user") UUID user, @Param("now") Instant now);

  @Modifying(flushAutomatically = true)
  @Query(
      "update AccessToken t set t.usedAt=:now where t.userId=:user and t.kind=:kind and t.usedAt is"
          + " null")
  int revokeKind(
      @Param("user") UUID user, @Param("kind") AccessToken.Kind kind, @Param("now") Instant now);

  List<AccessToken> findByUserIdInAndKindAndUsedAtIsNull(List<UUID> users, AccessToken.Kind kind);
}
