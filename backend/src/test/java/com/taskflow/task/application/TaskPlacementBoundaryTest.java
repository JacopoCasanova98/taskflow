package com.taskflow.task.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUser;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.task.persistence.TaskEntity;
import com.taskflow.task.persistence.TaskRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class TaskPlacementBoundaryTest {
	private static final UUID OWNER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
	private static final UUID BOARD = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
	private static final UUID SOURCE = UUID.fromString("750e8400-e29b-41d4-a716-446655440000");
	private static final UUID TARGET = UUID.fromString("850e8400-e29b-41d4-a716-446655440000");
	private static final UUID TASK = UUID.fromString("950e8400-e29b-41d4-a716-446655440000");

	@ParameterizedTest
	@ValueSource(ints = {-1, 1})
	void crossColumnPositionUsesTargetSizeAndFailsBeforeChangingEitherColumn(int position) {
		var identity = mock(AuthenticatedUserProvider.class);
		var boards = mock(BoardRepository.class);
		var columns = mock(ColumnRepository.class);
		var tasks = mock(TaskRepository.class);
		var service = new TaskService(identity, boards, columns, tasks);
		var board = new BoardEntity(OWNER, "Board");
		ReflectionTestUtils.setField(board, "id", BOARD);
		var source = new ColumnEntity(board, "Source", 0);
		var target = new ColumnEntity(board, "Target", 1);
		ReflectionTestUtils.setField(source, "id", SOURCE);
		ReflectionTestUtils.setField(target, "id", TARGET);
		var moving = new TaskEntity(source, "Moving", null, null, null, 0);
		var remaining = new TaskEntity(source, "Remaining", null, null, null, 1);
		ReflectionTestUtils.setField(moving, "id", TASK);
		ReflectionTestUtils.setField(remaining, "id", UUID.fromString("a50e8400-e29b-41d4-a716-446655440000"));
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OWNER));
		when(tasks.findBoardIdByIdAndOwnerId(TASK, OWNER)).thenReturn(Optional.of(BOARD));
		when(boards.findByIdAndOwnerIdForUpdate(BOARD, OWNER)).thenReturn(Optional.of(board));
		when(tasks.findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(TASK, BOARD, OWNER)).thenReturn(Optional.of(moving));
		when(columns.findByIdAndBoard_IdAndBoard_OwnerId(TARGET, BOARD, OWNER)).thenReturn(Optional.of(target));
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(SOURCE, OWNER))
				.thenReturn(List.of(moving, remaining));
		// The target is empty: only position zero is valid, even though the source has two tasks.

		assertThatThrownBy(() -> service.placeTask(TASK, TARGET, position))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("TASK_PLACEMENT_CONFLICT"));

		assertThat(List.of(moving, remaining)).extracting(TaskEntity::getPosition).containsExactly(0, 1);
		assertThat(List.of(moving, remaining)).allSatisfy(task -> assertThat(task.getColumn()).isSameAs(source));
		verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(TARGET, OWNER);
		verify(tasks, never()).flush();
		verify(tasks, never()).saveAndFlush(any());
		verify(tasks, never()).delete(any());
	}
}
