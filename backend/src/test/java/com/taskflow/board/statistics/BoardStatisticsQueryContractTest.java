package com.taskflow.board.statistics;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import com.taskflow.task.persistence.TaskRepository;
import com.taskflow.column.persistence.ColumnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Static contracts only: PostgreSQL aggregate execution belongs to MS6.4. */
class BoardStatisticsQueryContractTest {
	@ParameterizedTest
	@ValueSource(strings = {"aggregateBoardTotals", "aggregateBoardPriorities", "aggregateBoardColumns"})
	void everyAggregateUsesBoundBoardAndOwnerParameters(String name) {
		var method = Arrays.stream(TaskRepository.class.getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
		assertThat(method.getAnnotation(Query.class).value())
				.contains("t.column.board.id = :boardId", "t.column.board.ownerId = :ownerId", "count(t)");
		assertThat(Arrays.stream(method.getParameters()).map(p -> p.getAnnotation(Param.class).value()))
				.contains("boardId", "ownerId");
	}

	@Test
	void totalsUseStrictDueDateComparisonAndEmptySumCoalescing() throws Exception {
		var method = TaskRepository.class.getMethod("aggregateBoardTotals", UUID.class, UUID.class, LocalDate.class);
		assertThat(method.getAnnotation(Query.class).value())
				.contains("case when t.dueDate < :asOf then 1 else 0 end", "coalesce(sum(", "), 0) as overdue")
				.doesNotContain("<=", "current_date", "status", "completed");
		assertThat(method.getParameters()[2].getAnnotation(Param.class).value()).isEqualTo("asOf");
		// This is the specified civil-date boundary, not execution of the JPQL above.
		var day = LocalDate.of(2026, 9, 13);
		assertThat(Arrays.asList(day.minusDays(1), day, day.plusDays(1), null).stream()
				.filter(due -> due != null && due.isBefore(day))).containsExactly(day.minusDays(1));
	}

	@Test
	void distributionsAreGroupedAndColumnsHaveCanonicalDefensiveOrder() throws Exception {
		assertThat(TaskRepository.class.getMethod("aggregateBoardPriorities", UUID.class, UUID.class)
				.getAnnotation(Query.class).value()).contains("group by t.priority");
		assertThat(TaskRepository.class.getMethod("aggregateBoardColumns", UUID.class, UUID.class)
				.getAnnotation(Query.class).value()).contains("group by t.column.id");
		assertThat(ColumnRepository.class.getMethod("findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc", UUID.class, UUID.class))
				.isNotNull();
	}
}
