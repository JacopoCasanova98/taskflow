package com.taskflow.column.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ColumnRepository extends JpaRepository<ColumnEntity, UUID> {

	List<ColumnEntity> findAllByBoard_IdOrderByPositionAscIdAsc(UUID boardId);

	long countByBoard_Id(UUID boardId);

	Optional<ColumnEntity> findByIdAndBoard_OwnerId(UUID id, UUID ownerId);

	Optional<ColumnEntity> findByIdAndBoard_IdAndBoard_OwnerId(UUID id, UUID boardId, UUID ownerId);

	// Scalar discovery avoids managing stale Column state before acquiring the Board lock.
	@Query("select c.board.id from ColumnEntity c where c.id = :id and c.board.ownerId = :ownerId")
	Optional<UUID> findBoardIdByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

	// Flush deletion first. Bulk DML bypasses auditing; set the timestamp explicitly and
	// clear afterwards so shifted managed entities cannot retain obsolete positions.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ColumnEntity c set c.position = c.position - 1, c.updatedAt = :updatedAt "
			+ "where c.board.id = :boardId and c.position > :position")
	int compactAfterDeletion(@Param("boardId") UUID boardId, @Param("position") int position,
			@Param("updatedAt") Instant updatedAt);
}
