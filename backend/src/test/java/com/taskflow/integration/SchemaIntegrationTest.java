package com.taskflow.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.user.persistence.UserEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;

class SchemaIntegrationTest extends PostgresIntegrationSupport {
	@Autowired Flyway flyway;
	@Autowired Environment environment;
	@Autowired com.taskflow.auth.application.AuthenticationService authentication;
	@Autowired com.taskflow.auth.application.RefreshSessionLifecycle lifecycle;

	@Test
	void cleanSchemaHasAllSixSuccessfulMigrationsAndHibernateValidation() {
		assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
				String.class)).containsExactly("1", "2", "3", "4", "5", "6");
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("17.");
		assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conname IN "
				+ "('uq_columns_board_position', 'uq_tasks_column_position') AND condeferrable AND condeferred",
				String.class)).containsExactlyInAnyOrder("uq_columns_board_position", "uq_tasks_column_position");
	}

	@Test
	void normalizedEmailUniquenessAndRequiredPasswordAreEnforcedByPostgres() {
		var owner = user(" USER@EXAMPLE.COM ");
		assertThat(users.findById(owner.getId())).isPresent();
		assertThatThrownBy(() -> user("user@example.com")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> users.saveAndFlush(new UserEntity("other@example.com", null)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count("users")).isEqualTo(1);
	}

	@Test
	void refreshHashesForeignKeysAndReplacementHistoryPersist() {
		var owner = user("sessions@example.com");
		var expiry = Instant.parse("2030-01-01T00:00:00Z");
		var root = sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), "a".repeat(64), expiry));
		var child = sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), "b".repeat(64), expiry, root.getFamilyId()));
		transaction().executeWithoutResult(status -> {
			var managed = sessions.findById(root.getId()).orElseThrow();
			managed.markRotated(child.getId(), Instant.parse("2026-09-14T00:00:00Z"));
		});
		var reloaded = sessions.findById(root.getId()).orElseThrow();
		assertThat(reloaded.getReplacedByTokenId()).isEqualTo(child.getId());
		assertThat(reloaded.isRevoked()).isTrue();
		assertThat(sessions.findFamilyIdByTokenHash(child.getTokenHash())).contains(root.getFamilyId());
		UUID lockedRoot = transaction().execute(status -> sessions.lockFamilyRoot(root.getFamilyId()).orElseThrow().getId());
		assertThat(lockedRoot).isEqualTo(root.getId());
		assertThatThrownBy(() -> sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), root.getTokenHash(), expiry)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> sessions.saveAndFlush(new RefreshTokenEntity(
				UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), "c".repeat(64), expiry)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name='refresh_tokens'", String.class))
				.contains("token_hash").doesNotContain("token", "refresh_token", "raw_token");
		assertThat(count("refresh_tokens")).isEqualTo(2);
		users.deleteById(owner.getId());
		assertThat(count("refresh_tokens")).isZero();
	}

	@Test
	void boardAndUserDeletionCascadeThroughRealForeignKeys() {
		var owner = user("cascade@example.com");
		actAs(owner.getId());
		var first = boardService.createBoard("First");
		var column = columnService.createColumn(first.id(), "Column");
		taskService.createTask(column.id(), "Task", null, null, null);

		boardService.deleteBoard(first.id());

		assertThat(count("boards")).isZero();
		assertThat(count("columns")).isZero();
		assertThat(count("tasks")).isZero();
		assertThat(count("users")).isEqualTo(1);
		var second = boardService.createBoard("Second");
		var otherColumn = columnService.createColumn(second.id(), "Column");
		taskService.createTask(otherColumn.id(), "Task", null, null, null);
		sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), "d".repeat(64), Instant.parse("2030-01-01T00:00:00Z")));
		users.deleteById(owner.getId());
		for (String table : List.of("users", "boards", "columns", "tasks", "refresh_tokens")) {
			assertThat(count(table)).as(table).isZero();
		}
	}

	@Test
	void auditingAndCalendarDateAndTextPriorityRoundTrip() {
		var owner = user("audit@example.com");
		actAs(owner.getId());
		var board = boardService.createBoard("Board");
		assertThat(board.createdAt()).isNotNull();
		assertThat(board.updatedAt()).isNotNull();
		// Seed a historical timestamp instead of sleeping to overcome database timestamp precision.
		jdbc.update("UPDATE boards SET updated_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")), board.id());
		var renamed = boardService.renameBoard(board.id(), "Renamed");
		assertThat(renamed.updatedAt()).isAfter(Instant.parse("2000-01-01T00:00:00Z"));
		assertThat(renamed.createdAt()).isEqualTo(boards.findById(board.id()).orElseThrow().getCreatedAt());
		var column = columnService.createColumn(board.id(), "Column");
		var task = taskService.createTask(column.id(), "Task", null, TaskPriority.HIGH, LocalDate.of(2026, 9, 30));
		var reloaded = tasks.findById(task.id()).orElseThrow();
		assertThat(reloaded.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(reloaded.getPriority()).isEqualTo(TaskPriority.HIGH);
		assertThat(jdbc.queryForObject("SELECT priority FROM tasks WHERE id = ?", String.class, task.id())).isEqualTo("HIGH");
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void realRefreshRotationAndReplayRevokeOnlyTheAffectedFamily() {
		var registered = authentication.register("rotation@example.com", "synthetic integration password");
		var independent = authentication.login("rotation@example.com", "synthetic integration password", null);
		var rotated = lifecycle.refresh(registered.refreshToken()).orElseThrow();
		assertThat(lifecycle.refresh(registered.refreshToken())).isEmpty();
		assertThat(lifecycle.refresh(rotated.refreshToken())).isEmpty();
		assertThat(lifecycle.refresh(independent.refreshToken())).isPresent();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class))
				.isEqualTo(1);
	}
}
