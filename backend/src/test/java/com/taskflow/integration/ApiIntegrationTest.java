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
