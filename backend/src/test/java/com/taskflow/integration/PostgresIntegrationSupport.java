package com.taskflow.integration;

import java.util.UUID;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import com.taskflow.board.application.BoardService;
import com.taskflow.board.persistence.BoardRepository;
import com.taskflow.board.statistics.BoardStatisticsService;
import com.taskflow.column.application.ColumnService;
import com.taskflow.column.persistence.ColumnRepository;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.task.application.TaskService;
import com.taskflow.task.persistence.TaskRepository;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Import(PostgresIntegrationSupport.PostgresConfiguration.class)
abstract class PostgresIntegrationSupport {
	@TestConfiguration(proxyBeanMethods = false)
	static class PostgresConfiguration {
		@Bean
		@ServiceConnection
		PostgreSQLContainer<?> postgres() {
			return new PostgreSQLContainer<>("postgres:17-alpine");
		}
	}

	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired UserRepository users;
	@Autowired BoardRepository boards;
	@Autowired ColumnRepository columns;
	@Autowired TaskRepository tasks;
	@Autowired RefreshTokenRepository sessions;
	@Autowired BoardService boardService;
	@Autowired ColumnService columnService;
	@Autowired TaskService taskService;
	@Autowired BoardStatisticsService statistics;
	@Autowired AccessTokenService access;
	@Autowired JwtDecoder decoder;

	@BeforeEach
	void cleanDatabase() {
		SecurityContextHolder.clearContext();
		// No enclosing test transaction: service calls really commit, and failures really roll back.
		jdbc.execute("TRUNCATE TABLE tasks, columns, boards, refresh_tokens, users CASCADE");
	}

	@AfterEach
	void clearIdentity() {
		SecurityContextHolder.clearContext();
	}

	UserEntity user(String email) {
		return users.saveAndFlush(new UserEntity(email, "synthetic-unused-password-hash"));
	}

	void actAs(UUID userId) {
		// Service/repository tests only. HTTP tests obtain credentials through real registration.
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(org.springframework.security.oauth2.jwt.Jwt.withTokenValue("integration")
						.header("alg", "none").subject(userId.toString()).build(), java.util.List.of()));
	}

	TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}
}
