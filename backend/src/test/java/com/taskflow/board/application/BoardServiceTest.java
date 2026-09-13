package com.taskflow.board.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUser;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class BoardServiceTest {

	private static final UUID OWNER = UUID.fromString("7b361e53-9880-477a-9ea1-eac3d57b138b");
	private static final UUID OTHER = UUID.fromString("bc6517cf-8914-4818-8b47-37c8f838b657");
	private static final UUID ID = UUID.fromString("5ed6f9a7-a8c9-46c7-b673-d5f1699169f1");
	private static final Instant CREATED = Instant.parse("2026-09-10T10:00:00Z");
	private final AuthenticatedUserProvider identity = mock(AuthenticatedUserProvider.class);
	private final BoardRepository boards = mock(BoardRepository.class);
	private final BoardService service = new BoardService(identity, boards);

	@BeforeEach
	void currentOwner() {
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OWNER));
	}

	@Test
	void listsOnlyThroughTheCurrentOwnerQueryAndPreservesRepositoryOrder() {
		var newer = board(ID, "Newer", CREATED.plusSeconds(1));
		var older = board(UUID.fromString("006b5b90-b8e3-49ea-9c1e-f93858f7cb14"), "Older", CREATED);
		when(boards.findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER)).thenReturn(List.of(newer, older));
		assertThat(service.listBoards()).extracting(Board::name).containsExactly("Newer", "Older");
		verify(boards).findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER);
		verify(identity).currentUser();
		verifyNoMoreInteractions(boards);
	}

	@Test
	void emptyOwnerHasAnEmptyList() {
		assertThat(service.listBoards()).isEmpty();
		verify(boards).findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER);
		verifyNoMoreInteractions(boards);
	}

	@Test
	void readsSafeBoardThroughScopedLookup() {
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(board(ID, "Roadmap", CREATED)));
		assertThat(service.getBoard(ID)).isEqualTo(new Board(ID, "Roadmap", CREATED, CREATED));
		verify(boards).findByIdAndOwnerId(ID, OWNER);
		verify(identity).currentUser();
		verifyNoMoreInteractions(boards);
	}

	@Test
	void creationAlwaysUsesCurrentIdentityAndMapsAfterFlush() {
		when(boards.saveAndFlush(any(BoardEntity.class))).thenAnswer(invocation -> {
			BoardEntity board = invocation.getArgument(0);
			assertThat(board.getOwnerId()).isEqualTo(OWNER);
			assertThat(board.getName()).isEqualTo("Product   Roadmap");
			// Simulate persistence-assigned metadata; this does not test actual JPA auditing.
			ReflectionTestUtils.setField(board, "id", ID);
			ReflectionTestUtils.setField(board, "createdAt", CREATED);
			ReflectionTestUtils.setField(board, "updatedAt", CREATED);
			return board;
		});
		assertThat(service.createBoard("  Product   Roadmap  "))
				.isEqualTo(new Board(ID, "Product   Roadmap", CREATED, CREATED));
		verify(identity).currentUser();
		verify(boards).saveAndFlush(any(BoardEntity.class));
		verifyNoMoreInteractions(boards);
	}

	@Test
	void renamesOnlyTheScopedBoardAndMapsUpdatedAuditTimeAfterFlush() {
		var board = board(ID, "Original", CREATED);
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(board));
		Instant updated = CREATED.plusSeconds(60);
		when(boards.saveAndFlush(board)).thenAnswer(invocation -> {
			ReflectionTestUtils.setField(board, "updatedAt", updated);
			return board;
		});
		assertThat(service.renameBoard(ID, "  Updated  ")).isEqualTo(new Board(ID, "Updated", CREATED, updated));
		assertThat(board.getOwnerId()).isEqualTo(OWNER);
		var order = inOrder(boards);
		order.verify(boards).findByIdAndOwnerId(ID, OWNER);
		order.verify(boards).saveAndFlush(board);
		verify(identity).currentUser();
		verifyNoMoreInteractions(boards);
	}

	@Test
	void deletesOnlyAfterResolvingOwnedBoard() {
		var board = board(ID, "Roadmap", CREATED);
		when(boards.findByIdAndOwnerIdForUpdate(ID, OWNER)).thenReturn(Optional.of(board));
		service.deleteBoard(ID);
		var order = inOrder(boards);
		order.verify(boards).findByIdAndOwnerIdForUpdate(ID, OWNER);
		order.verify(boards).delete(board);
		verify(identity).currentUser();
		verifyNoMoreInteractions(boards);
	}

	@Test
	void otherOwnerAndAbsentBoardsHaveIdenticalFailuresWithoutWritesOrUnscopedReads() {
		var board = board(ID, "Private", CREATED);
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(board));
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(OTHER));
		UUID absent = UUID.fromString("dda0fabe-85a6-49fd-8291-b704bd605f85");
		for (UUID id : List.of(ID, absent)) {
			assertNotFound(() -> service.getBoard(id));
			assertNotFound(() -> service.renameBoard(id, "Changed"));
			assertNotFound(() -> service.deleteBoard(id));
			verify(boards, times(2)).findByIdAndOwnerId(id, OTHER);
			verify(boards).findByIdAndOwnerIdForUpdate(id, OTHER);
		}
		assertThat(board.getName()).isEqualTo("Private");
		verifyNoMoreInteractions(boards);
	}

	@Test
	void invalidDirectApplicationNamesNeverReachPersistenceWrites() {
		assertThatIllegalArgumentException().isThrownBy(() -> service.createBoard(" \t "));
		assertThatIllegalArgumentException().isThrownBy(() -> service.createBoard("x".repeat(121)));
		var board = board(ID, "Original", CREATED);
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(board));
		assertThatIllegalArgumentException().isThrownBy(() -> service.renameBoard(ID, " "));
		assertThat(board.getName()).isEqualTo("Original");
		verify(boards).findByIdAndOwnerId(ID, OWNER);
		verifyNoMoreInteractions(boards);
	}

	@Test
	void infrastructureFailureIsNotReportedAsNotFound() {
		var failure = new DataAccessResourceFailureException("private database details");
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenThrow(failure);
		assertThatThrownBy(() -> service.getBoard(ID)).isSameAs(failure);
	}

	private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
		assertThatThrownBy(operation).isInstanceOfSatisfying(ApiException.class, error -> {
			assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(error.getCode()).isEqualTo("BOARD_NOT_FOUND");
			assertThat(error.getTitle()).isEqualTo("Board not found");
			assertThat(error.getMessage()).isEqualTo("The requested board was not found.");
		});
	}

	private static BoardEntity board(UUID id, String name, Instant created) {
		var board = new BoardEntity(OWNER, name);
		ReflectionTestUtils.setField(board, "id", id);
		ReflectionTestUtils.setField(board, "createdAt", created);
		ReflectionTestUtils.setField(board, "updatedAt", created);
		return board;
	}
}
