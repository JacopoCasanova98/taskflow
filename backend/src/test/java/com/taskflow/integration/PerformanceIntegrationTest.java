package com.taskflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.api.dto.TaskResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Real committed PostgreSQL data, fresh service transactions, no wall-clock thresholds. */
class PerformanceIntegrationTest extends PostgresIntegrationSupport {
	private static final Logger LOG = LoggerFactory.getLogger(PerformanceIntegrationTest.class);
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired ObjectMapper mapper;
	private record Counts(long statements, long queries, long entities) {}
	private record Dataset(UUID boardId, List<UUID> columnIds, int taskCount) {}

	@Test
	void boardReadsDoNotGrowWithBoardCardinality() {
		actAs(user("boards@example.test").getId());
		var first = boardService.createBoard("First");
		var small = measure("one Board", () -> boardService.listBoards().size(), 1);
		for (int i = 1; i < 20; i++) boardService.createBoard("Board " + i);
		assertThat(measure("20 Boards", () -> boardService.listBoards().size(), 20)).isEqualTo(
				new Counts(small.statements(), small.queries(), 20));
		assertThat(measure("get Board", () -> boardService.getBoard(first.id()).id(), first.id()).statements())
				.isEqualTo(small.statements());
	}

	@Test
	void columnTaskSearchAndStatisticsReadsHaveCardinalityIndependentRoundTrips() {
		UUID owner = user("reads@example.test").getId();
		actAs(owner);
		var small = seed(1, 1);
		var large = seed(20, 100);
		var smallCounts = reads(small);
		var largeCounts = reads(large);
		for (int i = 0; i < smallCounts.size(); i++) {
			assertThat(largeCounts.get(i).statements()).isEqualTo(smallCounts.get(i).statements()).isBetween(1L, 6L);
			assertThat(largeCounts.get(i).queries()).isEqualTo(smallCounts.get(i).queries());
		}
	}

	private List<Counts> reads(Dataset data) {
		return List.of(
				measure("Board Tasks " + data.taskCount(), () -> taskService.listBoardTasks(data.boardId()).stream().map(TaskResponse::from).toList().size(), data.taskCount()),
				measure("Columns " + data.columnIds().size(), () -> columnService.listColumns(data.boardId()).size(), data.columnIds().size()),
				measure("Column Tasks", () -> taskService.listTasks(data.columnIds().getFirst()).stream().map(TaskResponse::from).toList().size(), data.taskCount() / data.columnIds().size()),
				measure("Search " + data.taskCount(), () -> taskService.searchTasks(data.boardId(), "synthetic").stream().map(TaskResponse::from).toList().size(), data.taskCount()),
				measure("Statistics " + data.taskCount(), () -> statistics.getBoardStatistics(data.boardId(), LocalDate.of(2026, 9, 17)).totalTasks(), (long) data.taskCount()));
	}

	@Test
	void appendDoesNotNeedExistingTaskContent() {
		actAs(user("append@example.test").getId());
		var small = seed(1, 1);
		var data = seed(1, 100);
		var before = measure("append to 1 Task", () -> taskService.createTask(small.columnIds().getFirst(), "New", null, null, null).position(), 1);
		var after = measure("append to 100 Tasks", () -> taskService.createTask(data.columnIds().getFirst(), "New", null, null, null).position(), 100);
		assertThat(after).isEqualTo(before);
		assertThat(after.entities()).isEqualTo(2); // Board + Column only, no existing Task hydration.
	}

	@Test
	void representativeSearchAndAggregatePlansAndPayload() throws Exception {
		UUID owner = user("plans@example.test").getId();
		actAs(owner);
		var data = seed(10, 100);
		jdbc.execute("ANALYZE boards");
		jdbc.execute("ANALYZE columns");
		jdbc.execute("ANALYZE tasks");
		String scope = " FROM tasks t JOIN columns c ON c.id=t.column_id JOIN boards b ON b.id=c.board_id WHERE b.id=? AND b.owner_id=?";
		explain("Search", "SELECT t.*" + scope
				+ " AND (lower(t.title) LIKE lower(?) ESCAPE '!' OR lower(t.description) LIKE lower(?) ESCAPE '!') ORDER BY c.position,t.position,t.id",
				data.boardId(), owner, "%synthetic%", "%synthetic%");
		explain("Totals", "SELECT count(*),coalesce(sum(case when t.due_date < DATE '2026-09-17' then 1 else 0 end),0)" + scope, data.boardId(), owner);
		explain("Priorities", "SELECT t.priority,count(*)" + scope + " GROUP BY t.priority", data.boardId(), owner);
		explain("Statuses", "SELECT t.column_id,count(*)" + scope + " GROUP BY t.column_id", data.boardId(), owner);
		var response = taskService.searchTasks(data.boardId(), "synthetic").stream().map(TaskResponse::from).toList();
		assertThat(response).hasSize(1000);
		assertThat(statistics.getBoardStatistics(data.boardId(), LocalDate.of(2026, 9, 17)).totalTasks()).isEqualTo(1000);
		LOG.info("MS6.8 synthetic Search response: tasks={}, JSON bytes={}", response.size(), mapper.writeValueAsBytes(response).length);
	}

	private void explain(String label, String sql, Object... args) {
		// Review evidence only: optimizer costs, timing and exact plan nodes are deliberately not asserted.
		LOG.info("MS6.8 PostgreSQL plan {}:\n{}", label,
				String.join("\n", jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class, args)));
	}

	private <T> Counts measure(String label, Supplier<T> operation, T expected) {
		var counters = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		boolean enabled = counters.isStatisticsEnabled();
		counters.setStatisticsEnabled(true);
		counters.clear(); // Seeding has already committed; no enclosing test persistence context.
		try {
			assertThat(operation.get()).isEqualTo(expected);
			var result = new Counts(counters.getPrepareStatementCount(), counters.getQueryExecutionCount(), counters.getEntityLoadCount());
			LOG.info("MS6.8 {}: {}", label, result);
			return result;
		} finally {
			counters.setStatisticsEnabled(enabled);
			counters.clear();
		}
	}

	private Dataset seed(int columnCount, int tasksPerColumn) {
		var board = boardService.createBoard("Synthetic performance Board");
		jdbc.update("""
				INSERT INTO columns (id,board_id,name,position,created_at,updated_at)
				SELECT gen_random_uuid(),?, 'Column ' || n,n,now(),now() FROM generate_series(0,?) n
				""", board.id(), columnCount - 1);
		var ids = jdbc.queryForList("SELECT id FROM columns WHERE board_id=? ORDER BY position", UUID.class, board.id());
		jdbc.update("""
				INSERT INTO tasks (id,column_id,title,description,priority,due_date,position,created_at,updated_at)
				SELECT gen_random_uuid(),c.id,'Synthetic task ' || n,'Short synthetic description','MEDIUM',DATE '2026-09-16',n,now(),now()
				FROM columns c CROSS JOIN generate_series(0,?) n WHERE c.board_id=?
				""", tasksPerColumn - 1, board.id());
		return new Dataset(board.id(), ids, columnCount * tasksPerColumn);
	}
}
