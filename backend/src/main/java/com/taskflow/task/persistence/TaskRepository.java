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

	@Query("""
			select t from TaskEntity t
			where t.column.board.id = :boardId and t.column.board.ownerId = :ownerId
			and (lower(t.title) like lower(:pattern) escape '!'
			     or lower(t.description) like lower(:pattern) escape '!')
			order by t.column.position asc, t.position asc, t.id asc
			""")
	List<TaskEntity> searchBoardTasks(@Param("boardId") UUID boardId, @Param("ownerId") UUID ownerId,
			@Param("pattern") String pattern);

	// Discover identity without managing stale placement/content before the Board lock.
	@Query("select t.column.board.id from TaskEntity t where t.id = :id and t.column.board.ownerId = :ownerId")
	Optional<UUID> findBoardIdByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
