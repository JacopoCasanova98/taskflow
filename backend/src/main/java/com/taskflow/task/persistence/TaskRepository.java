package com.taskflow.task.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
	List<TaskEntity> findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(UUID columnId, UUID ownerId);
	Optional<TaskEntity> findByIdAndColumn_Board_OwnerId(UUID id, UUID ownerId);
	Optional<TaskEntity> findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(UUID id, UUID boardId, UUID ownerId);
	boolean existsByColumn_Id(UUID columnId);

	// Discover identity without managing stale placement/content before the Board lock.
	@Query("select t.column.board.id from TaskEntity t where t.id = :id and t.column.board.ownerId = :ownerId")
	Optional<UUID> findBoardIdByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
