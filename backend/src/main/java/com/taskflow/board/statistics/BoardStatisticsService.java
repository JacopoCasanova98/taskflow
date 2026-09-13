package com.taskflow.board.statistics;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.UUID;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.task.persistence.TaskRepository;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.shared.error.ApiException;
import com.taskflow.board.statistics.BoardStatisticsResponse.PriorityDistribution;
import com.taskflow.board.statistics.BoardStatisticsResponse.StatusCount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardStatisticsService {
	private final AuthenticatedUserProvider identity;
	private final BoardRepository boards;
	private final ColumnRepository columns;
	private final TaskRepository tasks;

	public BoardStatisticsService(AuthenticatedUserProvider identity, BoardRepository boards,
			ColumnRepository columns, TaskRepository tasks) {
		this.identity = identity;
		this.boards = boards;
		this.columns = columns;
		this.tasks = tasks;
	}

	@Transactional(readOnly = true)
	public BoardStatisticsResponse getBoardStatistics(UUID boardId, LocalDate asOf) {
		var ownerId = identity.currentUser().id();
		boards.findByIdAndOwnerId(boardId, ownerId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "Board not found",
						"The requested board was not found."));
		var totals = tasks.aggregateBoardTotals(boardId, ownerId, asOf);
		var priorities = new EnumMap<TaskPriority, Long>(TaskPriority.class);
		for (var count : tasks.aggregateBoardPriorities(boardId, ownerId)) {
			priorities.put(count.getPriority(), count.getTaskCount());
		}
		var counts = new HashMap<UUID, Long>();
		for (var count : tasks.aggregateBoardColumns(boardId, ownerId)) {
			counts.put(count.getColumnId(), count.getTaskCount());
		}
		var statuses = columns.findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc(boardId, ownerId)
				.stream().map(column -> new StatusCount(column.getId(), column.getName(),
						column.getPosition(), counts.getOrDefault(column.getId(), 0L))).toList();
		return new BoardStatisticsResponse(totals.getTotal(), totals.getOverdue(),
				new PriorityDistribution(priorities.getOrDefault(TaskPriority.LOW, 0L),
						priorities.getOrDefault(TaskPriority.MEDIUM, 0L),
						priorities.getOrDefault(TaskPriority.HIGH, 0L)), statuses);
	}
}
