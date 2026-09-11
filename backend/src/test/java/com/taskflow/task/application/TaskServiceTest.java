package com.taskflow.task.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.board.persistence.*;
import com.taskflow.column.persistence.*;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.*;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.task.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class TaskServiceTest {
	private static final UUID OWNER = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();
	private static final UUID BOARD = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
	private final AuthenticatedUserProvider identity = mock(AuthenticatedUserProvider.class);
	private final BoardRepository boards = mock(BoardRepository.class);
	private final ColumnRepository columns = mock(ColumnRepository.class);
	private final TaskRepository tasks = mock(TaskRepository.class);
	private final TaskService service = new TaskService(identity, boards, columns, tasks);
	private final BoardEntity board = new BoardEntity(OWNER, "Board");
	private final ColumnEntity source = new ColumnEntity(board, "Source", 0);
	private final ColumnEntity target = new ColumnEntity(board, "Target", 1);

	@BeforeEach
	void setup() {
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OWNER));
		ReflectionTestUtils.setField(board, "id", BOARD);
		ReflectionTestUtils.setField(source, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(target, "id", UUID.randomUUID());
	}
	@Test
	void listChecksColumnOwnershipBeforeOrderedScopedRead() {
		when(columns.findByIdAndBoard_OwnerId(source.getId(), OWNER)).thenReturn(Optional.of(source));
		var a = task(source, "A", 0); var b = task(source, "B", 1);
		ordered(source, a, b);
		var result = service.listTasks(source.getId());
		assertThat(result).extracting(Task::title).containsExactly("A", "B");
		assertThat(result).extracting(Task::position).containsExactly(0, 1);
		assertThat(result.getFirst().columnId()).isEqualTo(source.getId());
		var order = inOrder(columns, tasks);
		order.verify(columns).findByIdAndBoard_OwnerId(source.getId(), OWNER);
		order.verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(source.getId(), OWNER);
	}
	@Test
	void ownedEmptyColumnListsEmpty() {
		when(columns.findByIdAndBoard_OwnerId(source.getId(), OWNER)).thenReturn(Optional.of(source));
		assertThat(service.listTasks(source.getId())).isEmpty();
	}
	@ParameterizedTest
	@ValueSource(ints = {0, 2})
	void createLocksRevalidatesAndAppendsBeforeMappingFlushedResult(int size) {
		parent();
		if (size == 2) ordered(source, task(source, "A", 0), task(source, "B", 1));
		when(tasks.saveAndFlush(any())).thenAnswer(inv -> {
			TaskEntity t = inv.getArgument(0); metadata(t); return t;
		});
		var result = service.createTask(source.getId(), "  Fix   Login  ", null, null, null);
		assertThat(result.position()).isEqualTo(size);
		assertThat(result.title()).isEqualTo("Fix   Login");
		assertThat(result.priority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(result.description()).isNull();
		assertThat(result.dueDate()).isNull();
		assertThat(result.id()).isNotNull();
		assertThat(result.createdAt()).isEqualTo(NOW);
		var order = inOrder(columns, boards, tasks);
		order.verify(columns).findBoardIdByIdAndOwnerId(source.getId(), OWNER);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).findByIdAndBoard_IdAndBoard_OwnerId(source.getId(), BOARD, OWNER);
		order.verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(source.getId(), OWNER);
		order.verify(tasks).saveAndFlush(any());
		verifyNoMoreInteractions(columns, boards, tasks);
	}
	@Test
	void getUsesOwnerScopedLookup() {
		var t = task(source, "A", 1);
		when(tasks.findByIdAndColumn_Board_OwnerId(t.getId(), OWNER)).thenReturn(Optional.of(t));
		assertThat(service.getTask(t.getId()).id()).isEqualTo(t.getId());
		verify(tasks).findByIdAndColumn_Board_OwnerId(t.getId(), OWNER);
		verifyNoMoreInteractions(tasks);
		verifyNoInteractions(boards, columns);
	}
	@Test
	void contentUpdateReFetchesAfterLockAndPreservesCurrentPlacement() {
		var t = task(target, "A", 2); direct(t);
		when(tasks.saveAndFlush(t)).thenAnswer(inv -> {
			ReflectionTestUtils.setField(t, "updatedAt", NOW.plusSeconds(1)); return t;
		});
		var date = LocalDate.of(2026, 9, 30);
		var result = service.updateTask(t.getId(), "New", "Description", TaskPriority.HIGH, date);
		assertThat(result.title()).isEqualTo("New");
		assertThat(result.description()).isEqualTo("Description");
		assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(result.dueDate()).isEqualTo(date);
		assertThat(result.columnId()).isEqualTo(target.getId());
		assertThat(result.position()).isEqualTo(2);
		assertThat(result.updatedAt()).isEqualTo(NOW.plusSeconds(1));
		var order = inOrder(tasks, boards);
		verifyDirectOrder(order, t);
		order.verify(tasks).saveAndFlush(t);
		verifyNoMoreInteractions(tasks, boards);
		result = service.updateTask(t.getId(), "New", null, TaskPriority.LOW, null);
		assertThat(result.description()).isNull();
		assertThat(result.dueDate()).isNull();
	}
	@Test
	void deleteMiddleCompactsSourceOnlyAfterOneLockAndRevalidation() {
		var a = task(source, "A", 0); var b = task(source, "B", 1); var c = task(source, "C", 2);
		var untouched = task(target, "Other", 2);
		direct(b); ordered(source, a, b, c);
		service.deleteTask(b.getId());
		assertThat(List.of(a, c)).extracting(TaskEntity::getPosition).containsExactly(0, 1);
		assertThat(untouched.getPosition()).isEqualTo(2);
		var order = inOrder(tasks, boards);
		verifyDirectOrder(order, b);
		order.verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(source.getId(), OWNER);
		order.verify(tasks).delete(b);
		order.verify(tasks).flush();
		verifyNoMoreInteractions(tasks, boards);
	}
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void sameColumnPlacementUsesRemoveThenInsertAndOneCollection(int position) {
		var a = task(source, "A", 0); var b = task(source, "B", 1); var c = task(source, "C", 2);
		direct(a); target(source); ordered(source, a, b, c);
		var result = service.placeTask(a.getId(), source.getId(), position);
		var expected = new java.util.ArrayList<>(List.of(b, c)); expected.add(position, a);
		assertThat(expected).extracting(TaskEntity::getPosition).containsExactly(0, 1, 2);
		assertThat(result.position()).isEqualTo(position);
		assertThat(result.columnId()).isEqualTo(source.getId());
		verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(source.getId(), OWNER);
		verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		verify(tasks).flush();
	}
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void crossColumnMoveCompactsBothAndPreservesContent(int position) {
		var a = task(source, "A", 0); var b = task(source, "B", 1); var c = task(source, "C", 2);
		var x = task(target, "X", 0); var y = task(target, "Y", 1);
		direct(b); target(target); ordered(source, a, b, c); ordered(target, x, y);
		var result = service.placeTask(b.getId(), target.getId(), position);
		assertThat(List.of(a, c)).extracting(TaskEntity::getPosition).containsExactly(0, 1);
		var expected = new java.util.ArrayList<>(List.of(x, y)); expected.add(position, b);
		assertThat(expected).extracting(TaskEntity::getPosition).containsExactly(0, 1, 2);
		assertThat(b.getColumn()).isSameAs(target);
		assertThat(b.getColumn().getBoard()).isSameAs(board);
		assertThat(result.title()).isEqualTo("B");
		assertThat(result.priority()).isEqualTo(TaskPriority.LOW);
		assertThat(result.description()).isEqualTo("Description");
		assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(result.columnId()).isEqualTo(target.getId());
		assertThat(result.position()).isEqualTo(position);
		var order = inOrder(tasks, boards, columns);
		verifyDirectOrder(order, b);
		order.verify(columns).findByIdAndBoard_IdAndBoard_OwnerId(target.getId(), BOARD, OWNER);
		order.verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(source.getId(), OWNER);
		order.verify(tasks).findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(target.getId(), OWNER);
		order.verify(tasks).flush();
		verifyNoMoreInteractions(tasks, boards, columns);
	}
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void placementIntoEmptyTargetAndRepeatedCurrentPlacementAreValid(boolean same) {
		var t = task(source, "A", 0); direct(t); target(same ? source : target); ordered(source, t);
		var result = service.placeTask(t.getId(), (same ? source : target).getId(), 0);
		assertThat(result.position()).isZero();
		assertThat(result.columnId()).isEqualTo((same ? source : target).getId());
	}
	@ParameterizedTest
	@ValueSource(ints = {-1, 2})
	void invalidPositionFailsBeforeAnyEntityMutation(int position) {
		var a = task(source, "A", 0); var b = task(source, "B", 1);
		direct(a); target(source); ordered(source, a, b);
		assertError(() -> service.placeTask(a.getId(), source.getId(), position), "TASK_PLACEMENT_CONFLICT", 409);
		assertThat(List.of(a, b)).extracting(TaskEntity::getPosition).containsExactly(0, 1);
		verify(tasks, never()).flush();
	}
	@ParameterizedTest
	@ValueSource(strings = {"missing", "otherBoard", "otherOwner"})
	void targetMustResolveWithinSourceOwnedBoard(String scenario) {
		var t = task(source, "A", 0); direct(t);
		UUID targetId = UUID.randomUUID();
		// Even a real target in another owned Board/user cannot satisfy the constrained query.
		if (!scenario.equals("missing")) {
			UUID otherBoard = UUID.randomUUID();
			when(columns.findByIdAndBoard_IdAndBoard_OwnerId(targetId, otherBoard,
					scenario.equals("otherOwner") ? OTHER : OWNER)).thenReturn(Optional.of(target));
		}
		assertError(() -> service.placeTask(t.getId(), targetId, 0), "COLUMN_NOT_FOUND", 404);
		verify(columns).findByIdAndBoard_IdAndBoard_OwnerId(targetId, BOARD, OWNER);
		verify(tasks, never()).flush();
		assertThat(t.getColumn()).isSameAs(source);
	}
	@ParameterizedTest
	@ValueSource(strings = {"create", "update", "delete", "place"})
	void disappearanceAfterDiscoveryOrLockReturnsSafeNotFound(String operation) {
		UUID id = UUID.randomUUID();
		if (operation.equals("create")) when(columns.findBoardIdByIdAndOwnerId(id, OWNER)).thenReturn(Optional.of(BOARD));
		else when(tasks.findBoardIdByIdAndOwnerId(id, OWNER)).thenReturn(Optional.of(BOARD));
		for (boolean parentExists : List.of(false, true)) {
			if (parentExists) lock();
			assertError(() -> mutate(operation, id), operation.equals("create") ? "COLUMN_NOT_FOUND" : "TASK_NOT_FOUND", 404);
		}
		verify(tasks, never()).saveAndFlush(any());
		verify(tasks, never()).delete(any());
		verify(tasks, never()).flush();
	}
	@Test
	void missingAndCrossUserResourcesFailWithoutUnscopedLookupsOrWrites() {
		var t = task(source, "Private", 0); direct(t); parent();
		when(tasks.findByIdAndColumn_Board_OwnerId(t.getId(), OWNER)).thenReturn(Optional.of(t));
		when(columns.findByIdAndBoard_OwnerId(source.getId(), OWNER)).thenReturn(Optional.of(source));
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OTHER));
		for (UUID id : List.of(source.getId(), UUID.randomUUID())) {
			assertError(() -> service.listTasks(id), "COLUMN_NOT_FOUND", 404);
			assertError(() -> mutate("create", id), "COLUMN_NOT_FOUND", 404);
		}
		for (UUID id : List.of(t.getId(), UUID.randomUUID())) {
			assertError(() -> service.getTask(id), "TASK_NOT_FOUND", 404);
			for (String op : List.of("update", "delete", "place")) assertError(() -> mutate(op, id), "TASK_NOT_FOUND", 404);
		}
		verify(tasks, never()).findById(any());
		verify(columns, never()).findById(any());
		verify(tasks, never()).saveAndFlush(any());
		verify(tasks, never()).delete(any());
		verify(tasks, never()).flush();
	}
	private void mutate(String op, UUID id) {
		switch (op) {
			case "create" -> service.createTask(id, "Title", null, null, null);
			case "update" -> service.updateTask(id, "Title", null, TaskPriority.HIGH, null);
			case "delete" -> service.deleteTask(id);
			case "place" -> service.placeTask(id, target.getId(), 0);
		}
	}
	private void verifyDirectOrder(org.mockito.InOrder order, TaskEntity task) {
		order.verify(tasks).findBoardIdByIdAndOwnerId(task.getId(), OWNER);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(tasks).findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(task.getId(), BOARD, OWNER);
	}
	private void lock() { when(boards.findByIdAndOwnerIdForUpdate(BOARD, OWNER)).thenReturn(Optional.of(board)); }
	private void target(ColumnEntity column) {
		when(columns.findByIdAndBoard_IdAndBoard_OwnerId(column.getId(), BOARD, OWNER)).thenReturn(Optional.of(column));
	}
	private void parent() {
		lock(); target(source);
		when(columns.findBoardIdByIdAndOwnerId(source.getId(), OWNER)).thenReturn(Optional.of(BOARD));
	}
	private void direct(TaskEntity task) {
		lock();
		when(tasks.findBoardIdByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(BOARD));
		when(tasks.findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(task.getId(), BOARD, OWNER)).thenReturn(Optional.of(task));
	}
	private void ordered(ColumnEntity column, TaskEntity... items) {
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(column.getId(), OWNER)).thenReturn(List.of(items));
	}
	private TaskEntity task(ColumnEntity column, String title, int position) {
		var task = new TaskEntity(column, title, "Description", TaskPriority.LOW, LocalDate.of(2026, 9, 15), position);
		metadata(task); return task;
	}
	private static void metadata(TaskEntity task) {
		ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(task, "createdAt", NOW);
		ReflectionTestUtils.setField(task, "updatedAt", NOW);
	}
	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code, int status) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code); assertThat(e.getStatus().value()).isEqualTo(status);
		});
	}
}
