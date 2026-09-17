package com.taskflow.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.auth.application.RefreshSessions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ApiIntegrationTest extends PostgresIntegrationSupport {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired com.taskflow.shared.security.jwt.AccessTokenService accessTokens;

	@Test
	void crossUserOperationsMatchMissingResourcesAndCannotChangePersistedWorkspace() throws Exception {
		var owner = user("owner@example.test").getId();
		actAs(owner);
		var board = boardService.createBoard("Private board");
		var column = columnService.createColumn(board.id(), "Private column");
		var task = taskService.createTask(column.id(), "Private task", null, null, null);
		clearIdentity();
		String bearer = accessTokens.issue(user("other@example.test").getId()).value();
		var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		for (boolean existing : new boolean[] {true, false}) {
			String boardId = (existing ? board.id() : UUID.randomUUID()).toString();
			String columnId = (existing ? column.id() : UUID.randomUUID()).toString();
			String taskId = (existing ? task.id() : UUID.randomUUID()).toString();
			assertPrivate404(secured(get("/api/boards/" + boardId), bearer, csrf), "BOARD_NOT_FOUND");
			assertPrivate404(json(secured(patch("/api/boards/" + boardId), bearer, csrf), Map.of("name", "Changed")), "BOARD_NOT_FOUND");
			assertPrivate404(secured(delete("/api/boards/" + boardId), bearer, csrf), "BOARD_NOT_FOUND");
			assertPrivate404(secured(get("/api/boards/" + boardId + "/columns"), bearer, csrf), "BOARD_NOT_FOUND");
			assertPrivate404(json(secured(post("/api/boards/" + boardId + "/columns"), bearer, csrf), Map.of("name", "New")), "BOARD_NOT_FOUND");
			assertPrivate404(json(secured(put("/api/boards/" + boardId + "/columns/order"), bearer, csrf), Map.of("columnIds", java.util.List.of(columnId))), "BOARD_NOT_FOUND");
			assertPrivate404(json(secured(patch("/api/columns/" + columnId), bearer, csrf), Map.of("name", "Changed")), "COLUMN_NOT_FOUND");
			assertPrivate404(secured(delete("/api/columns/" + columnId), bearer, csrf), "COLUMN_NOT_FOUND");
			assertPrivate404(secured(get("/api/columns/" + columnId + "/tasks"), bearer, csrf), "COLUMN_NOT_FOUND");
			assertPrivate404(json(secured(post("/api/columns/" + columnId + "/tasks"), bearer, csrf), Map.of("title", "New")), "COLUMN_NOT_FOUND");
			assertPrivate404(secured(get("/api/tasks/" + taskId), bearer, csrf), "TASK_NOT_FOUND");
			assertPrivate404(json(secured(put("/api/tasks/" + taskId), bearer, csrf), Map.of("title", "Changed", "priority", "HIGH")), "TASK_NOT_FOUND");
			assertPrivate404(secured(delete("/api/tasks/" + taskId), bearer, csrf), "TASK_NOT_FOUND");
			assertPrivate404(json(secured(put("/api/tasks/" + taskId + "/placement"), bearer, csrf), Map.of("columnId", columnId, "position", 0)), "TASK_NOT_FOUND");
			assertPrivate404(secured(get("/api/boards/" + boardId + "/tasks/search").param("q", "Private"), bearer, csrf), "BOARD_NOT_FOUND");
			assertPrivate404(secured(get("/api/boards/" + boardId + "/statistics").param("asOf", "2026-09-16"), bearer, csrf), "BOARD_NOT_FOUND");
		}
		actAs(owner);
		assertThat(boardService.getBoard(board.id())).isEqualTo(board);
		assertThat(columnService.listColumns(board.id())).containsExactly(column);
		assertThat(taskService.getTask(task.id())).isEqualTo(task);
	}

	private void assertPrivate404(MockHttpServletRequestBuilder request, String code) throws Exception {
		var body = mapper.readTree(mvc.perform(request).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString());
		assertThat(body.size()).isEqualTo(6);
		String resource = code.substring(0, code.indexOf('_')).toLowerCase(java.util.Locale.ROOT);
		assertThat(body.get("detail").asText()).isEqualTo("The requested " + resource + " was not found.");
	}

	@Test
	void registrationAndSecuredWorkspaceFlowCommitsRealRowsAndKeepsFailedMutationSafe() throws Exception {
		mvc.perform(get("/api/boards")).andExpect(status().isUnauthorized());
		var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		var registered = mvc.perform(json(post("/api/auth/register"), Map.of(
						"email", "integration@example.com", "password", "synthetic integration password"))
						.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isCreated()).andReturn().getResponse();
		var auth = mapper.readTree(registered.getContentAsString());
		String bearer = auth.get("accessToken").asText();
		UUID userId = UUID.fromString(auth.get("user").get("id").asText());
		csrf = registered.getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		var refresh = registered.getCookie("TASKFLOW_REFRESH");
		assertThat(refresh).isNotNull();
		assertThat(users.findById(userId)).isPresent();
		assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id=?", String.class, userId))
				.isEqualTo(RefreshSessions.hash(refresh.getValue())).isNotEqualTo(refresh.getValue());
		mvc.perform(json(post("/api/boards"), Map.of("name", "Rejected")).header("Authorization", "Bearer " + bearer))
				.andExpect(status().isForbidden());
		assertThat(count("boards")).isZero();

		var board = response(secured(post("/api/boards"), bearer, csrf), Map.of("name", "Board"), 201);
		String boardId = board.get("id").asText();
		assertThat(jdbc.queryForObject("SELECT owner_id FROM boards WHERE id=?", UUID.class, UUID.fromString(boardId))).isEqualTo(userId);
		var column = response(secured(post("/api/boards/" + boardId + "/columns"), bearer, csrf), Map.of("name", "Column"), 201);
		String columnId = column.get("id").asText();
		var task = response(secured(post("/api/columns/" + columnId + "/tasks"), bearer, csrf),
				Map.of("title", "Fix LOGIN flow", "priority", "HIGH", "dueDate", "2026-09-30"), 201);
		String taskId = task.get("id").asText();
		assertThat(tasks.existsById(UUID.fromString(taskId))).isTrue();
		mvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Fix LOGIN flow"));
		mvc.perform(get("/api/boards/" + boardId + "/tasks/search").param("q", "login")
						.header("Authorization", "Bearer " + bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(taskId));
		mvc.perform(get("/api/boards/" + boardId + "/statistics").param("asOf", "2026-09-30")
						.header("Authorization", "Bearer " + bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalTasks").value(1))
				.andExpect(jsonPath("$.overdueTasks").value(0));
		mvc.perform(secured(delete("/api/columns/" + columnId), bearer, csrf))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLUMN_NOT_EMPTY"))
				.andExpect(jsonPath("$.trace").doesNotExist());
		assertThat(columns.existsById(UUID.fromString(columnId))).isTrue();
		assertThat(tasks.existsById(UUID.fromString(taskId))).isTrue();
		mvc.perform(secured(delete("/api/tasks/" + taskId), bearer, csrf)).andExpect(status().isNoContent());
		assertThat(tasks.existsById(UUID.fromString(taskId))).isFalse();
	}

	private MockHttpServletRequestBuilder secured(MockHttpServletRequestBuilder request, String bearer, Cookie csrf) {
		return request.header("Authorization", "Bearer " + bearer).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
		return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
	}

	private JsonNode response(MockHttpServletRequestBuilder request, Map<String, ?> body, int expected) throws Exception {
		return mapper.readTree(mvc.perform(json(request, body)).andExpect(status().is(expected))
				.andReturn().getResponse().getContentAsString());
	}
}
