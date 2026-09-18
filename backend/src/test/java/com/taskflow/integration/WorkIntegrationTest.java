package com.taskflow.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.taskflow.column.application.Column;
import com.taskflow.shared.error.ApiException;
import com.taskflow.task.application.Task;
import com.taskflow.task.domain.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WorkIntegrationTest extends PostgresIntegrationSupport {
	private UUID owner;
	private UUID board;

	@BeforeEach
	void workspace() {
		owner = user("owner@example.com").getId();
		actAs(owner);
		board = boardService.createBoard("Board").id();
	}

	@Test
	void columnReorderAndDeletionCommitWithContiguousPositionsAndConstraintFailureRollsBack() {
		var a = columnService.createColumn(board, "A");
		var b = columnService.createColumn(board, "B");
		var c = columnService.createColumn(board, "C");
		columnService.reorderColumns(board, List.of(c.id(), a.id(), b.id()));
		assertThat(columnService.listColumns(board)).extracting(Column::id).containsExactly(c.id(), a.id(), b.id());
		assertThat(columnService.listColumns(board)).extracting(Column::position).containsExactly(0, 1, 2);
		columnService.deleteColumn(a.id());
		assertThat(columnService.listColumns(board)).extracting(Column::position).containsExactly(0, 1);
		var before = columnService.listColumns(board);
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			columnService.renameColumn(c.id(), "Must roll back");
			jdbc.update("UPDATE columns SET position=0 WHERE id=?", b.id());
			// The deferred constraint permits the update, but rejects the final duplicate at commit.
		})).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(columnService.listColumns(board)).isEqualTo(before);
	}

	@Test
	void taskMovesAndDeletionCommitWhileInvalidMovesAndNonEmptyDeletionPreserveDatabaseState() {
		var source = columnService.createColumn(board, "Source");
		var target = columnService.createColumn(board, "Target");
		var a = taskService.createTask(source.id(), "A", null, null, null);
		var b = taskService.createTask(source.id(), "B", null, null, null);
		var c = taskService.createTask(source.id(), "C", null, null, null);
		taskService.placeTask(a.id(), source.id(), 2);
		assertThat(taskService.listTasks(source.id())).extracting(Task::id).containsExactly(b.id(), c.id(), a.id());
		taskService.placeTask(c.id(), target.id(), 0);
		assertThat(taskService.listTasks(source.id())).extracting(Task::position).containsExactly(0, 1);
		assertThat(taskService.listTasks(target.id())).extracting(Task::position).containsExactly(0);
		taskService.deleteTask(b.id());
		assertThat(taskService.listTasks(source.id())).extracting(Task::position).containsExactly(0);
		var beforeSource = taskService.listTasks(source.id());
		var beforeTarget = taskService.listTasks(target.id());
		assertCode(() -> taskService.placeTask(a.id(), target.id(), 2), "TASK_PLACEMENT_CONFLICT");
		var otherBoard = boardService.createBoard("Other");
		var otherColumn = columnService.createColumn(otherBoard.id(), "Other");
		assertCode(() -> taskService.placeTask(a.id(), otherColumn.id(), 0), "COLUMN_NOT_FOUND");
		assertCode(() -> columnService.deleteColumn(source.id()), "COLUMN_NOT_EMPTY");
		assertThat(taskService.listTasks(source.id())).isEqualTo(beforeSource);
		assertThat(taskService.listTasks(target.id())).isEqualTo(beforeTarget);
		assertThat(columns.existsById(source.id())).isTrue();
	}

	@Test
	void deferredTaskConstraintRejectsFinalDuplicateAndRollsBackContentChange() {
		var column = columnService.createColumn(board, "Column");
		var a = taskService.createTask(column.id(), "A", null, null, null);
		var b = taskService.createTask(column.id(), "B", null, null, null);
		var before = taskService.listTasks(column.id());
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			taskService.updateTask(a.id(), "Must roll back", null, TaskPriority.HIGH, null);
			jdbc.update("UPDATE tasks SET position=0 WHERE id=?", b.id());
		})).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(taskService.listTasks(column.id())).isEqualTo(before);
	}

	@Test
	void scopedRepositoriesAndStatisticsHideAnotherUsersPersistedResources() {
		var column = columnService.createColumn(board, "Column");
		var task = taskService.createTask(column.id(), "Private", null, null, null);
		var other = user("other@example.com").getId();
		actAs(other);
		assertThat(boards.findByIdAndOwnerId(board, other)).isEmpty();
		assertThat(columns.findByIdAndBoard_OwnerId(column.id(), other)).isEmpty();
		assertThat(tasks.findByIdAndColumn_Board_OwnerId(task.id(), other)).isEmpty();
		assertCode(() -> boardService.getBoard(board), "BOARD_NOT_FOUND");
		assertCode(() -> taskService.getTask(task.id()), "TASK_NOT_FOUND");
		assertCode(() -> statistics.getBoardStatistics(board, LocalDate.of(2026, 9, 30)), "BOARD_NOT_FOUND");
	}

	@Test
	void searchExecutesCaseInsensitiveTitleOrDescriptionAndLiteralEscapingInCanonicalOrder() {
		var first = columnService.createColumn(board, "First");
		var second = columnService.createColumn(board, "Second");
		var later = taskService.createTask(second.id(), "LOGIN later", null, null, null);
		var title = taskService.createTask(first.id(), "Fix LOGIN flow", null, null, null);
		var description = taskService.createTask(first.id(), "Other", "login description", null, null);
		taskService.createTask(first.id(), "Unrelated", null, null, null);
		assertThat(taskService.searchTasks(board, "login")).extracting(Task::id)
				.containsExactly(title.id(), description.id(), later.id());
		for (String literal : List.of("%", "_", "!", "\\")) {
			var match = taskService.createTask(first.id(), "literal" + literal + "value", null, null, null);
			taskService.createTask(first.id(), "literalXvalue", null, null, null);
			assertThat(taskService.searchTasks(board, literal)).extracting(Task::id).containsExactly(match.id());
		}
		var otherBoard = boardService.createBoard("Excluded board");
		var otherColumn = columnService.createColumn(otherBoard.id(), "Column");
		taskService.createTask(otherColumn.id(), "LOGIN excluded", null, null, null);
		assertThat(taskService.searchTasks(board, "login")).hasSize(3);
	}

	@Test
	void statisticsExecutesAggregatesWithStrictDateBoundaryAndEmptyColumnZeroFill() {
		var asOf = LocalDate.of(2026, 9, 30);
		var first = columnService.createColumn(board, "First");
		var second = columnService.createColumn(board, "Second");
		columnService.createColumn(board, "Empty");
		taskService.createTask(first.id(), "Overdue", null, TaskPriority.HIGH, asOf.minusDays(1));
		taskService.createTask(first.id(), "Today", null, TaskPriority.LOW, asOf);
		taskService.createTask(second.id(), "Future", null, TaskPriority.MEDIUM, asOf.plusDays(1));
		taskService.createTask(second.id(), "Undated", null, TaskPriority.HIGH, null);
		var result = statistics.getBoardStatistics(board, asOf);
		assertThat(result.totalTasks()).isEqualTo(4);
		assertThat(result.overdueTasks()).isEqualTo(1);
		assertThat(result.priorityDistribution())
				.isEqualTo(new com.taskflow.board.statistics.BoardStatisticsResponse.PriorityDistribution(1, 1, 2));
		assertThat(result.statusDistribution()).extracting(com.taskflow.board.statistics.BoardStatisticsResponse.StatusCount::name)
				.containsExactly("First", "Second", "Empty");
		assertThat(result.statusDistribution()).extracting(com.taskflow.board.statistics.BoardStatisticsResponse.StatusCount::taskCount)
				.containsExactly(2L, 2L, 0L);
	}

	private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
				failure -> assertThat(failure.getCode()).isEqualTo(code));
	}
}
