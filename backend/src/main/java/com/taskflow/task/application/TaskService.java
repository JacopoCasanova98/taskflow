package com.taskflow.task.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.taskflow.shared.logging.MutationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.task.persistence.TaskEntity;
import com.taskflow.task.persistence.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
	private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);
	private final AuthenticatedUserProvider identity;
	private final BoardRepository boards;
	private final ColumnRepository columns;
	private final TaskRepository tasks;

	public TaskService(AuthenticatedUserProvider identity, BoardRepository boards,
			ColumnRepository columns, TaskRepository tasks) {
		this.identity = identity;
		this.boards = boards;
		this.columns = columns;
		this.tasks = tasks;
	}

	@Transactional(readOnly = true)
	public List<Task> listBoardTasks(UUID boardId) {
		UUID owner = identity.currentUser().id();
		boards.findByIdAndOwnerId(boardId, owner).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "Board not found", "The requested board was not found."));
		return tasks.listBoardTasks(boardId, owner).stream().map(TaskService::toTask).toList();
	}

	@Transactional(readOnly = true)
	public List<Task> searchTasks(UUID boardId, String query) {
		UUID owner = identity.currentUser().id();
		boards.findByIdAndOwnerId(boardId, owner).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "Board not found", "The requested board was not found."));
		String normalized = query.strip();
		if (normalized.length() > 200) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "Invalid search query",
					"Search queries must contain no more than 200 characters.");
		}
		if (normalized.isBlank()) return List.of();
		return tasks.searchBoardTasks(boardId, owner, searchPattern(normalized)).stream().map(TaskService::toTask).toList();
	}

	/** ! is the explicit LIKE escape; backslashes therefore remain literal. */
	static String searchPattern(String query) {
		return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}

	@Transactional(readOnly = true)
	public List<Task> listTasks(UUID columnId) {
		UUID owner = identity.currentUser().id();
		columns.findByIdAndBoard_OwnerId(columnId, owner).orElseThrow(TaskService::columnNotFound);
		return ordered(columnId, owner).stream().map(TaskService::toTask).toList();
	}

	@Transactional(readOnly = true)
	public Task getTask(UUID taskId) {
		return toTask(tasks.findByIdAndColumn_Board_OwnerId(taskId, identity.currentUser().id())
				.orElseThrow(TaskService::taskNotFound));
	}

	@Transactional
	public Task createTask(UUID columnId, String title, String description, TaskPriority priority, LocalDate dueDate) {
		UUID owner = identity.currentUser().id();
		UUID boardId = columns.findBoardIdByIdAndOwnerId(columnId, owner).orElseThrow(TaskService::columnNotFound);
		boards.findByIdAndOwnerIdForUpdate(boardId, owner).orElseThrow(TaskService::columnNotFound);
		var column = columns.findByIdAndBoard_IdAndBoard_OwnerId(columnId, boardId, owner)
				.orElseThrow(TaskService::columnNotFound);
		int position = Math.toIntExact(tasks.countByColumn_IdAndColumn_Board_OwnerId(columnId, owner));
		var result = toTask(tasks.saveAndFlush(new TaskEntity(column, title, description, priority, dueDate, position)));
		MutationLog.afterCommit(LOG, "event=task_created userId={} taskId={} columnId={}", owner, result.id(), columnId);
		return result;
	}

	@Transactional
	public Task updateTask(UUID taskId, String title, String description, TaskPriority priority, LocalDate dueDate) {
		UUID owner = identity.currentUser().id();
		var task = lockedTask(taskId, owner);
		task.updateContent(title, description, priority, dueDate);
		var result = toTask(tasks.saveAndFlush(task));
		MutationLog.afterCommit(LOG, "event=task_updated userId={} taskId={} columnId={}", owner, taskId, result.columnId());
		return result;
	}

	@Transactional
	public void deleteTask(UUID taskId) {
		UUID owner = identity.currentUser().id();
		var task = lockedTask(taskId, owner);
		var remaining = ordered(task.getColumn().getId(), owner);
		remaining.removeIf(item -> item.getId().equals(taskId));
		tasks.delete(task);
		resequence(remaining, task.getColumn());
		tasks.flush();
		MutationLog.afterCommit(LOG, "event=task_deleted userId={} taskId={}", owner, taskId);
	}

	@Transactional
	public Task placeTask(UUID taskId, UUID targetColumnId, int position) {
		UUID owner = identity.currentUser().id();
		var task = lockedTask(taskId, owner);
		var sourceColumn = task.getColumn();
		var targetColumn = columns.findByIdAndBoard_IdAndBoard_OwnerId(targetColumnId, sourceColumn.getBoard().getId(), owner)
				.orElseThrow(TaskService::columnNotFound);
		var source = ordered(sourceColumn.getId(), owner);
		source.removeIf(item -> item.getId().equals(taskId));
		boolean sameColumn = sourceColumn.getId().equals(targetColumnId);
		var target = sameColumn ? source : ordered(targetColumnId, owner);
		if (position < 0 || position > target.size()) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_PLACEMENT_CONFLICT", "Task placement conflict",
					"The requested task position is not valid for the target column.");
		}
		target.add(position, task);
		if (!sameColumn) resequence(source, sourceColumn);
		resequence(target, targetColumn);
		// Managed entities are dirty-checked; deferred uniqueness validates the final state at commit.
		tasks.flush();
		var result = toTask(task);
		MutationLog.afterCommit(LOG, "event=task_placed userId={} taskId={} sourceColumnId={} targetColumnId={} position={}",
				owner, taskId, sourceColumn.getId(), targetColumnId, position);
		return result;
	}

	private TaskEntity lockedTask(UUID taskId, UUID owner) {
		UUID boardId = tasks.findBoardIdByIdAndOwnerId(taskId, owner).orElseThrow(TaskService::taskNotFound);
		boards.findByIdAndOwnerIdForUpdate(boardId, owner).orElseThrow(TaskService::taskNotFound);
		return tasks.findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(taskId, boardId, owner)
				.orElseThrow(TaskService::taskNotFound);
	}

	private List<TaskEntity> ordered(UUID columnId, UUID owner) {
		return new ArrayList<>(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(columnId, owner));
	}

	private static void resequence(List<TaskEntity> ordered, ColumnEntity column) {
		for (int i = 0; i < ordered.size(); i++) ordered.get(i).place(column, i);
	}

	private static Task toTask(TaskEntity task) {
		return new Task(task.getId(), task.getColumn().getId(), task.getTitle(), task.getDescription(), task.getPriority(),
				task.getDueDate(), task.getPosition(), task.getCreatedAt(), task.getUpdatedAt());
	}

	private static ApiException taskNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found", "The requested task was not found.");
	}

	private static ApiException columnNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "COLUMN_NOT_FOUND", "Column not found", "The requested column was not found.");
	}
}
