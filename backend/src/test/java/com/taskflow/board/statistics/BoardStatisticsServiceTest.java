package com.taskflow.board.statistics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.task.persistence.TaskRepository;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.shared.security.AuthenticatedUser;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class BoardStatisticsServiceTest {
	private final UUID owner = UUID.randomUUID();
	private final UUID id = UUID.randomUUID();
	private final LocalDate asOf = LocalDate.of(2026, 9, 13);
	private final AuthenticatedUserProvider identity = mock(AuthenticatedUserProvider.class);
	private final BoardRepository boards = mock(BoardRepository.class);
	private final ColumnRepository columns = mock(ColumnRepository.class);
	private final TaskRepository tasks = mock(TaskRepository.class);
	private final BoardStatisticsService service = new BoardStatisticsService(identity, boards, columns, tasks);
	private final BoardEntity board = new BoardEntity(owner, "Private");

	@BeforeEach
	void setup() {
		when(identity.currentUser()).thenReturn(new AuthenticatedUser(owner));
	}

	@Test
	void aggregatesOwnedBoardWithFixedPriorityShapeAndCanonicalColumnsIncludingDoneZero() {
		owned(4, 1);
		var backlog = column("Backlog", 0);
		var doing = column("Doing", 1);
		var review = column("Review", 2);
		var done = column("Done", 3);
		when(columns.findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc(id, owner))
				.thenReturn(List.of(backlog, doing, review, done));
		var priorityCounts = List.of(priority(TaskPriority.HIGH, 2), priority(TaskPriority.LOW, 1), priority(TaskPriority.MEDIUM, 1));
		when(tasks.aggregateBoardPriorities(id, owner)).thenReturn(priorityCounts);
		var columnCounts = List.of(count(review, 1), count(backlog, 2), count(doing, 1));
		when(tasks.aggregateBoardColumns(id, owner)).thenReturn(columnCounts);
		var result = service.getBoardStatistics(id, asOf);
		assertThat(result.totalTasks()).isEqualTo(4);
		assertThat(result.overdueTasks()).isEqualTo(1);
		assertThat(result.priorityDistribution()).isEqualTo(new BoardStatisticsResponse.PriorityDistribution(1, 1, 2));
		assertThat(result.statusDistribution()).extracting(BoardStatisticsResponse.StatusCount::name)
				.containsExactly("Backlog", "Doing", "Review", "Done");
		assertThat(result.statusDistribution()).extracting(BoardStatisticsResponse.StatusCount::taskCount)
				.containsExactly(2L, 1L, 1L, 0L);
		assertThat(result.statusDistribution()).extracting(BoardStatisticsResponse.StatusCount::position)
				.containsExactly(0, 1, 2, 3);
		var order = inOrder(boards, tasks, columns);
		order.verify(boards).findByIdAndOwnerId(id, owner);
		order.verify(tasks).aggregateBoardTotals(id, owner, asOf);
		order.verify(tasks).aggregateBoardPriorities(id, owner);
		order.verify(tasks).aggregateBoardColumns(id, owner);
		order.verify(columns).findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc(id, owner);
		verifyNoMoreInteractions(boards, tasks, columns);
	}

	@Test
	void fillsMissingPriorityCategories() {
		owned(3, 0);
		var medium = priority(TaskPriority.MEDIUM, 3);
		when(tasks.aggregateBoardPriorities(id, owner)).thenReturn(List.of(medium));
		assertThat(service.getBoardStatistics(id, asOf).priorityDistribution())
				.isEqualTo(new BoardStatisticsResponse.PriorityDistribution(0, 3, 0));
	}

	@Test
	void emptyBoardReturnsZerosAndEmptyStatuses() {
		owned(0, 0);
		var result = service.getBoardStatistics(id, asOf);
		assertThat(result).isEqualTo(new BoardStatisticsResponse(0, 0,
				new BoardStatisticsResponse.PriorityDistribution(0, 0, 0), List.of()));
	}

	@Test
	void emptyColumnsAreRetainedWithoutCountingEachColumn() {
		owned(0, 0);
		when(columns.findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc(id, owner))
				.thenReturn(List.of(column("Backlog", 0), column("Doing", 1), column("Done", 2)));
		assertThat(service.getBoardStatistics(id, asOf).statusDistribution())
				.extracting(BoardStatisticsResponse.StatusCount::taskCount).containsExactly(0L, 0L, 0L);
		verify(tasks).aggregateBoardTotals(id, owner, asOf);
		verify(tasks).aggregateBoardPriorities(id, owner);
		verify(tasks).aggregateBoardColumns(id, owner);
		verifyNoMoreInteractions(tasks);
	}

	@Test
	void missingAndCrossUserBoardsHaveIdenticalNotFoundWithoutAggregation() {
		var other = UUID.randomUUID();
		when(boards.findByIdAndOwnerId(id, other)).thenReturn(Optional.of(board));
		for (var requested : List.of(id, UUID.randomUUID())) {
			assertThatThrownBy(() -> service.getBoardStatistics(requested, asOf))
					.isInstanceOfSatisfying(ApiException.class, error -> {
						assertThat(error.getCode()).isEqualTo("BOARD_NOT_FOUND");
						assertThat(error.getStatus().value()).isEqualTo(404);
						assertThat(error.getMessage()).isEqualTo("The requested board was not found.");
					});
			verify(boards).findByIdAndOwnerId(requested, owner);
		}
		verifyNoInteractions(tasks, columns);
	}

	@Test
	void useCaseIsReadOnly() throws Exception {
		assertThat(BoardStatisticsService.class.getMethod("getBoardStatistics", UUID.class, LocalDate.class)
				.getAnnotation(Transactional.class).readOnly()).isTrue();
	}

	private void owned(long total, long overdue) {
		when(boards.findByIdAndOwnerId(id, owner)).thenReturn(Optional.of(board));
		var totals = mock(TaskRepository.Totals.class);
		when(totals.getTotal()).thenReturn(total);
		when(totals.getOverdue()).thenReturn(overdue);
		when(tasks.aggregateBoardTotals(id, owner, asOf)).thenReturn(totals);
	}
	private ColumnEntity column(String name, int position) {
		var column = new ColumnEntity(board, name, position);
		ReflectionTestUtils.setField(column, "id", UUID.randomUUID());
		return column;
	}
	private TaskRepository.PriorityCount priority(TaskPriority priority, long count) {
		var result = mock(TaskRepository.PriorityCount.class);
		when(result.getPriority()).thenReturn(priority);
		when(result.getTaskCount()).thenReturn(count);
		return result;
	}
	private TaskRepository.ColumnCount count(ColumnEntity column, long count) {
		var result = mock(TaskRepository.ColumnCount.class);
		when(result.getColumnId()).thenReturn(column.getId());
		when(result.getTaskCount()).thenReturn(count);
		return result;
	}
}
