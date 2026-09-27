package com.taskflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(AuditingPrecisionIntegrationTest.FixedTimeConfiguration.class)
class AuditingPrecisionIntegrationTest extends PostgresIntegrationSupport {
	private static final Instant RAW = Instant.parse("2026-09-25T07:35:38.123456789Z");
	private static final Instant PERSISTABLE = Instant.parse("2026-09-25T07:35:38.123456Z");

	@Test
	void committedRepresentationsMatchReloadsWithNanosecondClock() {
		var owner = user("precision@example.test");
		assertThat(owner.getCreatedAt()).isEqualTo(PERSISTABLE);
		assertThat(owner.getUpdatedAt()).isEqualTo(PERSISTABLE);
		var reloaded = users.findById(owner.getId()).orElseThrow();
		assertThat(reloaded.getCreatedAt()).isEqualTo(owner.getCreatedAt());
		assertThat(reloaded.getUpdatedAt()).isEqualTo(owner.getUpdatedAt());
		actAs(owner.getId());
		var board = boardService.createBoard("Precision");
		var column = columnService.createColumn(board.id(), "Precision");
		var task = taskService.createTask(column.id(), "Precision", null, null, null);
		assertThat(board.createdAt()).isEqualTo(PERSISTABLE);
		assertThat(column.createdAt()).isEqualTo(PERSISTABLE);
		assertThat(task.createdAt()).isEqualTo(PERSISTABLE);
		// Each service call commits independently; reads load actual PostgreSQL rows.
		assertThat(boardService.getBoard(board.id())).isEqualTo(board);
		assertThat(columnService.listColumns(board.id())).containsExactly(column);
		assertThat(taskService.getTask(task.id())).isEqualTo(task);
	}

	@Test
	void jdbcRoundsRawNanosecondsButPreservesNormalizedMicroseconds() {
		assertThat(jdbc.queryForObject("SELECT CAST(? AS timestamp with time zone)",
				Timestamp.class, Timestamp.from(RAW)).toInstant())
				.isEqualTo(Instant.parse("2026-09-25T07:35:38.123457Z"));
		assertThat(jdbc.queryForObject("SELECT CAST(? AS timestamp with time zone)",
				Timestamp.class, Timestamp.from(PERSISTABLE)).toInstant()).isEqualTo(PERSISTABLE);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTimeConfiguration {
		@Bean
		@Primary
		Clock fixedAuditClock() {
			return Clock.fixed(RAW, ZoneOffset.UTC);
		}
	}
}
