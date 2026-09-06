package com.taskflow.user.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	// Callers must normalize email with UserEmailNormalizer before querying.
	Optional<UserEntity> findByEmail(String normalizedEmail);

	boolean existsByEmail(String normalizedEmail);
}
