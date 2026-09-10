package com.taskflow.board.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<BoardEntity, UUID> {

	List<BoardEntity> findAllByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId);

	Optional<BoardEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
