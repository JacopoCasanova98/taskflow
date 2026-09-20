package com.taskflow.operations;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.taskflow.BackendApplication;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import com.taskflow.board.application.BoardService;
import com.taskflow.column.application.ColumnService;
import com.taskflow.task.application.TaskService;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DatabaseMigrationIntegrationTest {
	@Container
	static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("taskflow");

	@Test
	void bootstrapMigrateAndRunWithLeastPrivilegeWithoutAws() throws Exception {
		String migrationPassword = "NOT-A-SECRET-fixture-" + UUID.randomUUID();
		String appPassword = "NOT-A-SECRET-fixture-" + UUID.randomUUID();
		String operatorPassword = "NOT-A-SECRET-fixture-" + UUID.randomUUID();
		String bootstrap = Files.readString(Path.of("../scripts/production/bootstrap-database.sql"));
		try (Connection admin = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())) {
			admin.createStatement().execute("CREATE ROLE fixture_operator LOGIN CREATEROLE PASSWORD '" + operatorPassword + "'");
			admin.createStatement().execute("ALTER DATABASE taskflow OWNER TO fixture_operator");
		}
		// Ordinary database-owning CREATEROLE operator, not a PostgreSQL superuser.
		try (Connection admin = DriverManager.getConnection(DATABASE.getJdbcUrl(), "fixture_operator", operatorPassword)) {
			admin.createStatement().execute(bootstrap);
			// Disposable fixture passwords only; none are operator values or process arguments.
			admin.createStatement().execute("ALTER ROLE taskflow_migrator PASSWORD '" + migrationPassword + "'");
			admin.createStatement().execute("ALTER ROLE taskflow_app PASSWORD '" + appPassword + "'");
			DatabaseMigration.migrate(DATABASE.getJdbcUrl(), migrationPassword);
			DatabaseMigration.migrate(DATABASE.getJdbcUrl(), migrationPassword); // No duplicate history.
			try (Connection migration = DriverManager.getConnection(DATABASE.getJdbcUrl(), "taskflow_migrator", migrationPassword)) {
				migration.createStatement().execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT TRUNCATE ON TABLES TO taskflow_app");
			}
			admin.createStatement().execute(bootstrap); // Reset excessive defaults, preserve passwords and existing objects.
		}

		try (Connection app = DriverManager.getConnection(DATABASE.getJdbcUrl(), "taskflow_app", appPassword);
				Connection migrator = DriverManager.getConnection(DATABASE.getJdbcUrl(), "taskflow_migrator", migrationPassword)) {
			assertThat(query(app, "SELECT current_user")).isEqualTo("taskflow_app");
			assertThat(query(migrator, "SELECT current_user")).isEqualTo("taskflow_migrator");
			assertThat(query(app, "SELECT count(*) FROM flyway_schema_history WHERE success")).isEqualTo("6");
			assertThat(query(app, "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner <> 'taskflow_migrator'"))
					.isEqualTo("0");
			assertThat(query(app, "SELECT count(*) FROM pg_roles WHERE rolname IN ('taskflow_app','taskflow_migrator') "
					+ "AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)" )).isEqualTo("0");
			for (String sql : new String[] {"CREATE TABLE public.forbidden (id int)", "DROP TABLE public.tasks",
					"ALTER TABLE public.tasks ADD COLUMN forbidden int", "CREATE ROLE forbidden_role",
					"CREATE DATABASE forbidden_database", "CREATE SCHEMA forbidden_schema", "CREATE TEMP TABLE forbidden_temp (id int)",
					"TRUNCATE tasks", "UPDATE flyway_schema_history SET checksum=0", "SET ROLE taskflow_migrator"}) {
				assertThatThrownBy(() -> app.createStatement().execute(sql)).as(sql)
						.isInstanceOfSatisfying(SQLException.class, exception -> assertThat(exception.getSQLState()).isEqualTo("42501"));
			}
			migrator.createStatement().execute("CREATE TABLE future_fixture (id bigint GENERATED BY DEFAULT AS IDENTITY, value text)");
			app.createStatement().execute("INSERT INTO future_fixture(value) VALUES ('fixture')");
			assertThat(query(app, "SELECT id FROM future_fixture")).isEqualTo("1");
			app.createStatement().execute("UPDATE future_fixture SET value='updated'");
			app.createStatement().execute("DELETE FROM future_fixture");
			assertThatThrownBy(() -> app.createStatement().execute("TRUNCATE future_fixture"))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> app.createStatement().execute("SELECT setval('future_fixture_id_seq', 5)"))
					.isInstanceOf(SQLException.class);
		}

		// Real backend wiring with Flyway off and Hibernate validate; local PG is deliberately not an RDS TLS test.
		Map<String, Object> inputs = Map.of(
				"spring.datasource.url", DATABASE.getJdbcUrl(), "spring.datasource.username", "taskflow_app",
				"spring.datasource.password", appPassword, "spring.flyway.enabled", "false",
				"spring.jpa.hibernate.ddl-auto", "validate", "server.port", "0", "server.address", "127.0.0.1",
				"taskflow.security.jwt.secret-base64", Base64.getEncoder().encodeToString(new byte[32]));
		try (var context = new SpringApplicationBuilder(BackendApplication.class)
				.initializers(c -> c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("local-role-fixture", inputs)))
				.run()) {
			assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("taskflow_app");
			UserRepository users = context.getBean(UserRepository.class);
			var user = users.saveAndFlush(new UserEntity("role-fixture@example.invalid", "NOT-A-SECRET-hash-fixture"));
			SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("fixture")
					.header("alg", "none").subject(user.getId().toString()).build(), java.util.List.of()));
			try {
				var boards = context.getBean(BoardService.class);
				var columns = context.getBean(ColumnService.class);
				var tasks = context.getBean(TaskService.class);
				var board = boards.createBoard("Role fixture");
				var column = columns.createColumn(board.id(), "Column");
				var task = tasks.createTask(column.id(), "Create", null, null, null);
				tasks.updateTask(task.id(), "Updated", null, TaskPriority.HIGH, null);
				assertThat(tasks.getTask(task.id()).title()).isEqualTo("Updated");
				tasks.deleteTask(task.id());
				boards.deleteBoard(board.id());
				context.getBean(RefreshTokenRepository.class).saveAndFlush(new RefreshTokenEntity(user.getId(),
						"a".repeat(64), Instant.now().plusSeconds(60)));
				users.deleteById(user.getId());
				assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
			} finally {
				SecurityContextHolder.clearContext();
			}
			// Simulate a migration validation failure; no deployment action follows and old runtime remains healthy.
			try (Connection admin = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())) {
				admin.createStatement().execute("UPDATE flyway_schema_history SET checksum=0 WHERE version='1'");
			}
			AtomicBoolean deploymentStarted = new AtomicBoolean();
			assertThatThrownBy(() -> {
				DatabaseMigration.migrate(DATABASE.getJdbcUrl(), migrationPassword);
				deploymentStarted.set(true);
			}).isInstanceOf(Exception.class);
			assertThat(deploymentStarted).isFalse();
			assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("taskflow_app");
		}
	}

	private static String query(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
			result.next();
			return result.getString(1);
		}
	}
}
