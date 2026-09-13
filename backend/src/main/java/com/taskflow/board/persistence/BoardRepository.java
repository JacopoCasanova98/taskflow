package com.taskflow.board.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<BoardEntity, UUID> {

	List<BoardEntity> findAllByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId);

	Optional<BoardEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BoardEntity b where b.id = :id and b.ownerId = :ownerId")
	Optional<BoardEntity> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
