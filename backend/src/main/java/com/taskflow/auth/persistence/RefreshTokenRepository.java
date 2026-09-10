package com.taskflow.auth.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
	@Query("select token.familyId from RefreshTokenEntity token where token.tokenHash = :hash")
	Optional<UUID> findFamilyIdByTokenHash(@Param("hash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select root from RefreshTokenEntity root where root.familyId = :familyId and not exists "
			+ "(select parent.id from RefreshTokenEntity parent where parent.familyId = :familyId "
			+ "and parent.replacedByTokenId = root.id)")
	Optional<RefreshTokenEntity> lockFamilyRoot(@Param("familyId") UUID familyId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshTokenEntity token where token.tokenHash = :hash")
	Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("hash") String tokenHash);

	@Modifying(flushAutomatically = true)
	@Query("update RefreshTokenEntity token set token.revokedAt = :now, token.updatedAt = :now "
			+ "where token.familyId = :familyId and token.revokedAt is null")
	int revokeActiveByFamilyId(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
