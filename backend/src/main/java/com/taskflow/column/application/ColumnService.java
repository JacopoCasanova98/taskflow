package com.taskflow.column.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColumnService {

	private final AuthenticatedUserProvider identity;
	private final BoardRepository boards;
	private final ColumnRepository columns;
	private final Clock clock;

	public ColumnService(AuthenticatedUserProvider identity, BoardRepository boards,
			ColumnRepository columns, Clock clock) {
		this.identity = identity;
		this.boards = boards;
		this.columns = columns;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Column> listColumns(UUID boardId) {
		boards.findByIdAndOwnerId(boardId, identity.currentUser().id()).orElseThrow(ColumnService::boardNotFound);
		return columns.findAllByBoard_IdOrderByPositionAscIdAsc(boardId).stream().map(ColumnService::toColumn).toList();
	}

	@Transactional
	public Column createColumn(UUID boardId, String name) {
		var board = lockBoard(boardId, identity.currentUser().id());
		int position = Math.toIntExact(columns.countByBoard_Id(boardId));
		return toColumn(columns.saveAndFlush(new ColumnEntity(board, name, position)));
	}

	@Transactional
	public Column renameColumn(UUID columnId, String name) {
		var column = lockedColumn(columnId, identity.currentUser().id());
		column.rename(name);
		return toColumn(columns.saveAndFlush(column));
	}

	@Transactional
	public void deleteColumn(UUID columnId) {
		var column = lockedColumn(columnId, identity.currentUser().id());
		UUID boardId = column.getBoard().getId();
		int position = column.getPosition();
		// MS5.4 must enforce COLUMN_NOT_EMPTY when Task persistence exists.
		columns.delete(column);
		columns.compactAfterDeletion(boardId, position, clock.instant());
	}

	@Transactional
	public List<Column> reorderColumns(UUID boardId, List<UUID> orderedIds) {
		lockBoard(boardId, identity.currentUser().id());
		var current = columns.findAllByBoard_IdOrderByPositionAscIdAsc(boardId);
		var byId = new HashMap<UUID, ColumnEntity>();
		current.forEach(column -> byId.put(column.getId(), column));
		if (orderedIds == null || orderedIds.size() != current.size()
				|| new HashSet<>(orderedIds).size() != orderedIds.size()
				|| !byId.keySet().equals(new HashSet<>(orderedIds))) {
			throw new ApiException(HttpStatus.CONFLICT, "COLUMN_ORDER_CONFLICT", "Column order conflict",
					"The submitted column order does not match the board's current columns.");
		}
		var ordered = orderedIds.stream().map(byId::get).toList();
		for (int position = 0; position < ordered.size(); position++) {
			ordered.get(position).assignPosition(position);
		}
		// Dirty checking updates managed entities; deferred uniqueness checks final state at commit.
		columns.flush();
		return ordered.stream().map(ColumnService::toColumn).toList();
	}

	private BoardEntity lockBoard(UUID boardId, UUID ownerId) {
		return boards.findByIdAndOwnerIdForUpdate(boardId, ownerId).orElseThrow(ColumnService::boardNotFound);
	}

	private ColumnEntity lockedColumn(UUID columnId, UUID ownerId) {
		UUID boardId = columns.findBoardIdByIdAndOwnerId(columnId, ownerId).orElseThrow(ColumnService::columnNotFound);
		// A concurrently deleted parent still yields the direct endpoint's safe Column 404.
		boards.findByIdAndOwnerIdForUpdate(boardId, ownerId).orElseThrow(ColumnService::columnNotFound);
		return columns.findByIdAndBoard_OwnerId(columnId, ownerId).orElseThrow(ColumnService::columnNotFound);
	}

	private static ApiException boardNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "Board not found",
				"The requested board was not found.");
	}

	private static ApiException columnNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "COLUMN_NOT_FOUND", "Column not found",
				"The requested column was not found.");
	}

	private static Column toColumn(ColumnEntity column) {
		return new Column(column.getId(), column.getName(), column.getPosition(), column.getCreatedAt(), column.getUpdatedAt());
	}
}
