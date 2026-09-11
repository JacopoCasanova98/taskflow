package com.taskflow.column.persistence;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import com.taskflow.board.persistence.BoardRepository;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.LockModeType;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Static contracts only: these tests do not execute JPA queries or PostgreSQL locks. */
class ColumnPersistenceContractTest {
	@Test
	void parentMutationQueryLocksOnlyTheOwnedBoard() throws Exception {
		var method = BoardRepository.class.getMethod("findByIdAndOwnerIdForUpdate", UUID.class, UUID.class);
		assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(method.getAnnotation(Query.class).value()).contains("b.id = :id", "b.ownerId = :ownerId");
	}

	@Test
	void columnParentIsRequiredLazyAndNotUpdatable() throws Exception {
		var field = ColumnEntity.class.getDeclaredField("board");
		assertThat(field.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
		assertThat(field.getAnnotation(ManyToOne.class).optional()).isFalse();
		assertThat(field.getAnnotation(JoinColumn.class).name()).isEqualTo("board_id");
		assertThat(field.getAnnotation(JoinColumn.class).nullable()).isFalse();
		assertThat(field.getAnnotation(JoinColumn.class).updatable()).isFalse();
	}

	@Test
	void compactionFlushesDeletionClearsStaleEntitiesAndUpdatesOnlyLaterPositionsInBoard() throws Exception {
		var method = ColumnRepository.class.getMethod("compactAfterDeletion", UUID.class, int.class, Instant.class);
		assertThat(method.getAnnotation(Modifying.class).flushAutomatically()).isTrue();
		assertThat(method.getAnnotation(Modifying.class).clearAutomatically()).isTrue();
		assertThat(method.getAnnotation(Query.class).value()).contains("c.position = c.position - 1",
				"c.updatedAt = :updatedAt", "c.board.id = :boardId", "c.position > :position");
	}
}
