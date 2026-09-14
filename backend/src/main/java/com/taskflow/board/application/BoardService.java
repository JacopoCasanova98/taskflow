package com.taskflow.board.application;

import java.util.List;
import java.util.UUID;
import com.taskflow.shared.logging.MutationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardService {
	private static final Logger LOG = LoggerFactory.getLogger(BoardService.class);

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
		var result = toBoard(boards.saveAndFlush(board));
		MutationLog.afterCommit(LOG, "event=board_created userId={} boardId={}", board.getOwnerId(), result.id());
		return result;
	}

	@Transactional
	public Board renameBoard(UUID boardId, String name) {
		var board = ownedBoard(boardId);
		board.rename(name);
		// Flush before mapping so the response includes the updated auditing timestamp.
		var result = toBoard(boards.saveAndFlush(board));
		MutationLog.afterCommit(LOG, "event=board_renamed userId={} boardId={}", board.getOwnerId(), result.id());
		return result;
	}

	@Transactional
	public void deleteBoard(UUID boardId) {
		var board = boards.findByIdAndOwnerIdForUpdate(boardId, identity.currentUser().id())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND",
						"Board not found", "The requested board was not found."));
		boards.delete(board);
		MutationLog.afterCommit(LOG, "event=board_deleted userId={} boardId={}", board.getOwnerId(), boardId);
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
