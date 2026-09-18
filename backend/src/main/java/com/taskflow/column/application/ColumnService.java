package com.taskflow.column.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import com.taskflow.shared.logging.MutationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger LOG = LoggerFactory.getLogger(ColumnService.class);

	private final AuthenticatedUserProvider identity;
	private final BoardRepository boards;
	private final ColumnRepository columns;
	private final Clock clock;
	private final ColumnTaskPresence taskPresence;

	public ColumnService(AuthenticatedUserProvider identity, BoardRepository boards,
			ColumnRepository columns, Clock clock, ColumnTaskPresence taskPresence) {
		this.identity = identity;
		this.boards = boards;
		this.columns = columns;
		this.clock = clock;
		this.taskPresence = taskPresence;
	}

	@Transactional(readOnly = true)
	public List<Column> listColumns(UUID boardId) {
		boards.findByIdAndOwnerId(boardId, identity.currentUser().id()).orElseThrow(ColumnService::boardNotFound);
		return columns.findAllByBoard_IdOrderByPositionAscIdAsc(boardId).stream().map(ColumnService::toColumn).toList();
	}

	@Transactional
	public Column createColumn(UUID boardId, String name) {
		UUID owner = identity.currentUser().id();
		var board = lockBoard(boardId, owner);
		int position = Math.toIntExact(columns.countByBoard_Id(boardId));
		var result = toColumn(columns.saveAndFlush(new ColumnEntity(board, name, position)));
		MutationLog.afterCommit(LOG, "event=column_created userId={} boardId={} columnId={}", owner, boardId, result.id());
		return result;
	}

	@Transactional
	public Column renameColumn(UUID columnId, String name) {
		UUID owner = identity.currentUser().id();
		var column = lockedColumn(columnId, owner);
		column.rename(name);
		var result = toColumn(columns.saveAndFlush(column));
		MutationLog.afterCommit(LOG, "event=column_renamed userId={} columnId={}", owner, columnId);
		return result;
	}

	@Transactional
	public void deleteColumn(UUID columnId) {
		UUID owner = identity.currentUser().id();
		var column = lockedColumn(columnId, owner);
		UUID boardId = column.getBoard().getId();
		int position = column.getPosition();
		if (taskPresence.hasTasks(columnId)) {
			throw new ApiException(HttpStatus.CONFLICT, "COLUMN_NOT_EMPTY", "Column not empty",
					"The column must be empty before it can be deleted.");
		}
		columns.delete(column);
		columns.compactAfterDeletion(boardId, position, clock.instant());
		MutationLog.afterCommit(LOG, "event=column_deleted userId={} boardId={} columnId={}", owner, boardId, columnId);
	}

	@Transactional
	public List<Column> reorderColumns(UUID boardId, List<UUID> orderedIds) {
		UUID owner = identity.currentUser().id();
		lockBoard(boardId, owner);
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
		var result = ordered.stream().map(ColumnService::toColumn).toList();
		MutationLog.afterCommit(LOG, "event=columns_reordered userId={} boardId={} count={}", owner, boardId, result.size());
		return result;
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
