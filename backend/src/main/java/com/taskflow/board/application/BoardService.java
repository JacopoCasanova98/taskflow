package com.taskflow.board.application;

import java.util.List;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardService {

	private final AuthenticatedUserProvider identity;
	private final BoardRepository boards;

	public BoardService(AuthenticatedUserProvider identity, BoardRepository boards) {
		this.identity = identity;
		this.boards = boards;
	}

	@Transactional(readOnly = true)
	public List<Board> listBoards() {
		return boards.findAllByOwnerIdOrderByCreatedAtDescIdDesc(identity.currentUser().id())
				.stream().map(BoardService::toBoard).toList();
	}

	@Transactional(readOnly = true)
	public Board getBoard(UUID boardId) {
		return toBoard(ownedBoard(boardId));
	}

	@Transactional
	public Board createBoard(String name) {
		var board = new BoardEntity(identity.currentUser().id(), name);
		return toBoard(boards.saveAndFlush(board));
	}

	@Transactional
	public Board renameBoard(UUID boardId, String name) {
		var board = ownedBoard(boardId);
		board.rename(name);
		// Flush before mapping so the response includes the updated auditing timestamp.
		return toBoard(boards.saveAndFlush(board));
	}

	@Transactional
	public void deleteBoard(UUID boardId) {
		boards.delete(ownedBoard(boardId));
	}

	private BoardEntity ownedBoard(UUID boardId) {
		return boards.findByIdAndOwnerId(boardId, identity.currentUser().id())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND",
						"Board not found", "The requested board was not found."));
	}

	private static Board toBoard(BoardEntity board) {
		return new Board(board.getId(), board.getName(), board.getCreatedAt(), board.getUpdatedAt());
	}
}
