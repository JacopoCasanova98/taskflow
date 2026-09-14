package com.taskflow.column.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class ColumnServiceTest {
	private static final UUID OWNER = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();
	private static final UUID BOARD = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
	private final AuthenticatedUserProvider identity = mock(AuthenticatedUserProvider.class);
	private final BoardRepository boards = mock(BoardRepository.class);
	private final ColumnRepository columns = mock(ColumnRepository.class);
	private final ColumnTaskPresence taskPresence = mock(ColumnTaskPresence.class);
	private final ColumnService service = new ColumnService(identity, boards, columns, Clock.fixed(NOW, ZoneOffset.UTC), taskPresence);
	private final BoardEntity board = new BoardEntity(OWNER, "Board");

	@BeforeEach
	void setup() {
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OWNER));
		ReflectionTestUtils.setField(board, "id", BOARD);
	}

	@Test
	void listChecksOwnershipBeforeOrderedReadAndMapsOnlySafeData() {
		when(boards.findByIdAndOwnerId(BOARD, OWNER)).thenReturn(Optional.of(board));
		var a = column("A", 0);
		var b = column("B", 1);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(a, b));
		assertThat(service.listColumns(BOARD)).containsExactly(result(a), result(b));
		var order = inOrder(boards, columns);
		order.verify(boards).findByIdAndOwnerId(BOARD, OWNER);
		order.verify(columns).findAllByBoard_IdOrderByPositionAscIdAsc(BOARD);
		verifyNoMoreInteractions(boards, columns);
	}

	@Test
	void emptyOwnedBoardListsEmpty() {
		when(boards.findByIdAndOwnerId(BOARD, OWNER)).thenReturn(Optional.of(board));
		assertThat(service.listColumns(BOARD)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 3})
	void createLocksBeforeCountingAndAppendsWithCorrectRelationAndAuditResponse(int count) {
		lock();
		when(columns.countByBoard_Id(BOARD)).thenReturn((long) count);
		when(columns.saveAndFlush(any())).thenAnswer(invocation -> {
			ColumnEntity c = invocation.getArgument(0);
			assertThat(c.getBoard()).isSameAs(board);
			assertThat(c.getPosition()).isEqualTo(count);
			assertThat(c.getName()).isEqualTo("In   Progress");
			metadata(c);
			return c;
		});
		var result = service.createColumn(BOARD, "  In   Progress  ");
		assertThat(result.position()).isEqualTo(count);
		assertThat(result.id()).isNotNull();
		assertThat(result.createdAt()).isEqualTo(NOW);
		assertThat(result.updatedAt()).isEqualTo(NOW);
		var order = inOrder(boards, columns);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).countByBoard_Id(BOARD);
		order.verify(columns).saveAndFlush(any());
		verify(identity).currentUser();
		verifyNoMoreInteractions(boards, columns);
	}

	@Test
	void renameLoadsFreshScopedEntityAfterLockAndChangesOnlyName() {
		var c = column("Original", 2);
		direct(c);
		when(columns.saveAndFlush(c)).thenAnswer(invocation -> {
			ReflectionTestUtils.setField(c, "updatedAt", NOW.plusSeconds(1));
			return c;
		});
		assertThat(service.renameColumn(c.getId(), "  Next   Step  ").updatedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(c.getName()).isEqualTo("Next   Step");
		assertThat(c.getPosition()).isEqualTo(2);
		assertThat(c.getBoard()).isSameAs(board);
		var order = inOrder(columns, boards);
		order.verify(columns).findBoardIdByIdAndOwnerId(c.getId(), OWNER);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).findByIdAndBoard_OwnerId(c.getId(), OWNER);
		order.verify(columns).saveAndFlush(c);
		verifyNoMoreInteractions(boards, columns);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 3})
	void deleteLocksAndRevalidatesBeforeDeletionAndSingleBulkCompaction(int position) {
		var c = column("Delete", position);
		direct(c);
		service.deleteColumn(c.getId());
		var order = inOrder(columns, boards, taskPresence);
		order.verify(columns).findBoardIdByIdAndOwnerId(c.getId(), OWNER);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).findByIdAndBoard_OwnerId(c.getId(), OWNER);
		order.verify(taskPresence).hasTasks(c.getId());
		order.verify(columns).delete(c);
		order.verify(columns).compactAfterDeletion(BOARD, position, NOW);
		verifyNoMoreInteractions(columns, boards);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void disappearingParentOrColumnAfterDiscoveryProducesColumnNotFound(boolean parentStillExists) {
		UUID id = UUID.randomUUID();
		when(columns.findBoardIdByIdAndOwnerId(id, OWNER)).thenReturn(Optional.of(BOARD));
		if (parentStillExists) lock();
		assertError(() -> service.deleteColumn(id), "COLUMN_NOT_FOUND", 404,
				"Column not found", "The requested column was not found.");
		verify(columns, never()).delete(any());
		verify(columns, never()).compactAfterDeletion(any(), anyInt(), any());
	}

	@Test
	void crossUserAndMissingParentsAndChildrenFailIdenticallyWithoutWrites() {
		var c = column("Private", 0);
		when(boards.findByIdAndOwnerId(BOARD, OWNER)).thenReturn(Optional.of(board));
		when(columns.findBoardIdByIdAndOwnerId(c.getId(), OWNER)).thenReturn(Optional.of(BOARD));
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OTHER));
		for (UUID id : List.of(BOARD, UUID.randomUUID())) {
			assertBoardNotFound(() -> service.listColumns(id));
			assertBoardNotFound(() -> service.createColumn(id, "Name"));
			assertBoardNotFound(() -> service.reorderColumns(id, List.of()));
			verify(boards).findByIdAndOwnerId(id, OTHER);
			verify(boards, times(2)).findByIdAndOwnerIdForUpdate(id, OTHER);
		}
		for (UUID id : List.of(c.getId(), UUID.randomUUID())) {
			assertError(() -> service.renameColumn(id, "Name"), "COLUMN_NOT_FOUND", 404,
					"Column not found", "The requested column was not found.");
			assertError(() -> service.deleteColumn(id), "COLUMN_NOT_FOUND", 404,
					"Column not found", "The requested column was not found.");
			verify(columns, times(2)).findBoardIdByIdAndOwnerId(id, OTHER);
		}
		verifyNoMoreInteractions(boards, columns);
	}

	@Test
	void nonEmptyColumnCannotBeDeletedOrResequenced() {
		var c = column("Keep", 1); direct(c);
		when(taskPresence.hasTasks(c.getId())).thenReturn(true);
		assertError(() -> service.deleteColumn(c.getId()), "COLUMN_NOT_EMPTY", 409,
				"Column not empty", "The column must be empty before it can be deleted.");
		var order = inOrder(columns, boards, taskPresence);
		order.verify(columns).findBoardIdByIdAndOwnerId(c.getId(), OWNER);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).findByIdAndBoard_OwnerId(c.getId(), OWNER);
		order.verify(taskPresence).hasTasks(c.getId());
		verify(columns, never()).delete(any());
		verify(columns, never()).compactAfterDeletion(any(), anyInt(), any());
		assertThat(c.getPosition()).isEqualTo(1);
	}

	@Test
	void reorderReturnsCanonicalOrderAndIsIdempotent() {
		lock();
		var a = column("A", 0); var b = column("B", 1); var c = column("C", 2);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(a, b, c));
		List<UUID> submitted = List.of(c.getId(), a.getId(), b.getId());
		var result = service.reorderColumns(BOARD, submitted);
		assertThat(result).extracting(Column::name).containsExactly("C", "A", "B");
		assertThat(result).extracting(Column::position).containsExactly(0, 1, 2);
		assertThat(List.of(a, b, c)).allSatisfy(entity -> assertThat(entity.getBoard()).isSameAs(board));
		var order = inOrder(boards, columns);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, OWNER);
		order.verify(columns).findAllByBoard_IdOrderByPositionAscIdAsc(BOARD);
		order.verify(columns).flush();
		assertThat(service.reorderColumns(BOARD, submitted)).isEqualTo(result);
	}

	@ParameterizedTest
	@ValueSource(strings = {"duplicate", "missing", "extra", "foreign", "empty", "nullElement", "nullList"})
	void invalidReordersFailBeforeAnyMutationOrFlush(String scenario) {
		lock();
		var a = column("A", 0); var b = column("B", 1); var c = column("C", 2);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(a, b, c));
		List<UUID> ids = switch (scenario) {
			case "duplicate" -> List.of(a.getId(), a.getId(), c.getId());
			case "missing" -> List.of(a.getId(), b.getId());
			case "extra" -> List.of(a.getId(), b.getId(), c.getId(), UUID.randomUUID());
			case "foreign" -> List.of(a.getId(), b.getId(), UUID.randomUUID());
			case "empty" -> List.of();
			case "nullElement" -> java.util.Arrays.asList(a.getId(), b.getId(), null);
			default -> null;
		};
		assertError(() -> service.reorderColumns(BOARD, ids), "COLUMN_ORDER_CONFLICT", 409,
				"Column order conflict", "The submitted column order does not match the board's current columns.");
		assertThat(List.of(a, b, c)).extracting(ColumnEntity::getPosition).containsExactly(0, 1, 2);
		verify(columns, never()).flush();
		verify(columns).findAllByBoard_IdOrderByPositionAscIdAsc(BOARD);
		verifyNoMoreInteractions(columns);
	}

	@Test
	void emptyReorderIsValidOnEmptyOwnedBoard() {
		lock();
		assertThat(service.reorderColumns(BOARD, List.of())).isEmpty();
		verify(columns).flush();
	}

	@Test
	void invalidApplicationNamesNeverWrite() {
		lock();
		assertThatIllegalArgumentException().isThrownBy(() -> service.createColumn(BOARD, " "));
		var c = column("Original", 1); direct(c);
		assertThatIllegalArgumentException().isThrownBy(() -> service.renameColumn(c.getId(), "x".repeat(121)));
		assertThat(c.getName()).isEqualTo("Original");
		verify(columns, never()).saveAndFlush(any());
	}

	@Test
	void reorderFlushFailurePropagatesForTransactionRollback() {
		lock();
		var c = column("C", 0);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(c));
		var failure = new DataAccessResourceFailureException("private failure");
		doThrow(failure).when(columns).flush();
		assertThatThrownBy(() -> service.reorderColumns(BOARD, List.of(c.getId()))).isSameAs(failure);
	}

	private void lock() {
		when(boards.findByIdAndOwnerIdForUpdate(BOARD, OWNER)).thenReturn(Optional.of(board));
	}

	private void direct(ColumnEntity c) {
		lock();
		when(columns.findBoardIdByIdAndOwnerId(c.getId(), OWNER)).thenReturn(Optional.of(BOARD));
		when(columns.findByIdAndBoard_OwnerId(c.getId(), OWNER)).thenReturn(Optional.of(c));
	}

	private ColumnEntity column(String name, int position) {
		var c = new ColumnEntity(board, name, position); metadata(c); return c;
	}

	private static void metadata(ColumnEntity c) {
		ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(c, "createdAt", NOW);
		ReflectionTestUtils.setField(c, "updatedAt", NOW);
	}

	private static Column result(ColumnEntity c) {
		return new Column(c.getId(), c.getName(), c.getPosition(), NOW, NOW);
	}

	private static void assertBoardNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertError(action, "BOARD_NOT_FOUND", 404, "Board not found", "The requested board was not found.");
	}

	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
			String code, int status, String title, String detail) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, error -> {
			assertThat(error.getCode()).isEqualTo(code);
			assertThat(error.getStatus().value()).isEqualTo(status);
			assertThat(error.getTitle()).isEqualTo(title);
			assertThat(error.getMessage()).isEqualTo(detail);
		});
	}

	@Test
	void creationLogContainsOnlySafeIdentifiers() {
		lock();
		when(columns.saveAndFlush(any())).thenAnswer(inv -> {
			ColumnEntity entity = inv.getArgument(0); metadata(entity); return entity;
		});
		try (var logs = new com.taskflow.shared.logging.LogCapture(ColumnService.class)) {
			var result = service.createColumn(BOARD, "PRIVATE_COLUMN_NAME");
			assertThat(logs.events()).hasSize(1);
			assertThat(logs.messages()).contains("event=column_created", "userId=" + OWNER,
					"boardId=" + BOARD, "columnId=" + result.id()).doesNotContain("PRIVATE_COLUMN_NAME");
		}
	}

}
