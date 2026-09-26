package com.taskflow.shared.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.DatabaseFreePersistenceTest;
import com.taskflow.shared.security.jwt.AccessTokenService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest extends DatabaseFreePersistenceTest {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired AccessTokenService tokens;
	@Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;

	private JsonNode document() throws Exception {
		return mapper.readTree(mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	void exposesMetadataAndOnlyApplicationPathsWithCompleteOperationCoverage() throws Exception {
		JsonNode api = document();
		assertThat(api.path("openapi").asText()).startsWith("3.");
		assertThat(api.at("/info/title").asText()).isEqualTo("TaskFlow API");
		assertThat(api.at("/info/version").asText()).isEqualTo("0.0.1-SNAPSHOT");
		Set<String> actual = new HashSet<>();
		Set<String> ids = new HashSet<>();
		Set<String> tags = new HashSet<>();
		api.path("paths").fields().forEachRemaining(path -> {
			assertThat(path.getKey()).startsWith("/api/");
			path.getValue().fields().forEachRemaining(operation -> {
				actual.add(operation.getKey().toUpperCase() + " " + path.getKey());
				JsonNode value = operation.getValue();
				assertThat(value.path("summary").asText()).isNotBlank();
				assertThat(ids.add(value.path("operationId").asText())).isTrue();
				value.path("tags").forEach(tag -> tags.add(tag.asText()));
				assertThat(value.path("responses").has("500")).isTrue();
			});
		});
		Set<String> implemented = new HashSet<>();
		requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
			for (String path : mapping.getPatternValues()) if (path.startsWith("/api/")) {
				mapping.getMethodsCondition().getMethods().forEach(method -> implemented.add(method.name() + " " + path));
			}
		});
		assertThat(actual).hasSize(25).isEqualTo(implemented);
		assertThat(api.path("tags").findValuesAsText("name")).containsExactlyInAnyOrderElementsOf(tags);
		assertThat(tags).containsExactlyInAnyOrder("Authentication", "Boards", "Columns", "Tasks", "Board Statistics");
		assertThat(api.path("paths").properties()).extracting(Map.Entry::getKey).contains(
				"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/csrf", "/api/auth/me",
				"/api/boards", "/api/boards/{boardId}/columns", "/api/columns/{columnId}/tasks",
				"/api/tasks/{taskId}", "/api/tasks/{taskId}/placement", "/api/boards/{boardId}/tasks/search",
				"/api/boards/{boardId}/statistics");
	}

	@Test
	void describesCanonicalBoardTaskReadUsingTheExistingTaskSchema() throws Exception {
		var operation = document().path("paths").path("/api/boards/{boardId}/tasks").path("get");
		assertThat(operation.path("tags")).containsExactly(mapper.valueToTree("Tasks"));
		assertThat(operation.path("description").asText()).contains("Column position, Task position, then Task ID");
		assertThat(operation.at("/responses/200/content/application~1json/schema/type").asText()).isEqualTo("array");
		assertThat(operation.at("/responses/200/content/application~1json/schema/items/$ref").asText()).endsWith("/TaskResponse");
		assertThat(operation.at("/responses/404/$ref").asText()).endsWith("/BoardNotFound");
		assertThat(operation.path("security").findValues("bearerAuth")).hasSize(1);
	}

	@Test
	void documentsBearerOnlyForProtectedOperationsAndCsrfForEveryMutation() throws Exception {
		JsonNode api = document();
		JsonNode scheme = api.at("/components/securitySchemes/bearerAuth");
		assertThat(scheme.path("type").asText()).isEqualTo("http");
		assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
		assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
		assertThat(api.has("security")).isFalse();
		api.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(entry -> {
			boolean protectedOperation = !path.getKey().startsWith("/api/auth/") || path.getKey().equals("/api/auth/me");
			JsonNode operation = entry.getValue();
			assertThat(operation.path("security").findValues("bearerAuth")).hasSize(protectedOperation ? 1 : 0);
			if (!entry.getKey().equals("get")) {
				assertThat(operation.path("parameters").findValuesAsText("name")).contains("X-XSRF-TOKEN");
				assertThat(operation.at("/responses/403/$ref").asText()).endsWith("/AccessDenied");
			}
		}));
		for (String path : List.of("login", "refresh", "logout")) {
			assertThat(api.path("paths").path("/api/auth/" + path).path("post").path("parameters").findValuesAsText("in"))
					.doesNotContain("cookie");
		}
	}

	@Test
	void describesDtoTypesAndExistingValidationWithoutLeakingEntitiesOrCredentials() throws Exception {
		JsonNode schemas = document().at("/components/schemas");
		for (String name : List.of("BoardResponse", "ColumnResponse", "TaskResponse")) {
			JsonNode properties = schemas.path(name).path("properties");
			assertThat(properties.path("id").path("type").asText()).isEqualTo("string");
			assertThat(properties.path("id").path("format").asText()).isEqualTo("uuid");
			assertThat(properties.path("createdAt").path("format").asText()).isEqualTo("date-time");
			assertThat(properties.path("updatedAt").path("format").asText()).isEqualTo("date-time");
			assertThat(properties.has("ownerId")).isFalse();
			assertThat(properties.has("password")).isFalse();
		}
		assertThat(schemas.at("/TaskResponse/properties/priority/enum")).containsExactly(
				mapper.valueToTree("LOW"), mapper.valueToTree("MEDIUM"), mapper.valueToTree("HIGH"));
		assertThat(schemas.at("/TaskResponse/properties/dueDate/format").asText()).isEqualTo("date");
		assertThat(schemas.at("/CreateBoardRequest/additionalProperties").asBoolean(true)).isFalse();
		assertThat(schemas.at("/TaskResponse/properties/dueDate/type")).containsExactly(
				mapper.valueToTree("string"), mapper.valueToTree("null"));
		assertThat(schemas.at("/CreateBoardRequest/properties/name/maxLength").asInt()).isEqualTo(120);
		assertThat(schemas.at("/CreateColumnRequest/properties/name/maxLength").asInt()).isEqualTo(120);
		assertThat(schemas.at("/CreateTaskRequest/properties/priority/anyOf/0/enum")).containsExactly(
				mapper.valueToTree("LOW"), mapper.valueToTree("MEDIUM"), mapper.valueToTree("HIGH"));
		assertThat(schemas.at("/CreateTaskRequest/properties/priority/anyOf/1/type").asText()).isEqualTo("null");
		assertThat(schemas.at("/CreateTaskRequest/properties/title/maxLength").asInt()).isEqualTo(200);
		assertThat(schemas.at("/CreateTaskRequest/properties/description/maxLength").asInt()).isEqualTo(4000);
		assertThat(schemas.at("/RegisterRequest/properties/password/minLength").asInt()).isEqualTo(15);
		assertThat(schemas.at("/RegisterRequest/properties/password/maxLength").asInt()).isEqualTo(128);
		assertThat(schemas.at("/RegisterRequest/properties/password/writeOnly").asBoolean()).isTrue();
		assertThat(schemas.at("/RegisterRequest/properties/email/maxLength").asInt()).isEqualTo(254);
		assertThat(schemas.at("/CurrentUserResponse/properties/password").isMissingNode()).isTrue();
		assertThat(schemas.toString()).doesNotContain("Entity", "passwordHash", "refreshToken", "ownerId");
	}

	@Test
	void describesRealSuccessStatusesAndWildcardAuthResponseSchemas() throws Exception {
		JsonNode paths = document().path("paths");
		for (String path : List.of("/api/boards", "/api/boards/{boardId}/columns", "/api/columns/{columnId}/tasks", "/api/auth/register")) {
			assertThat(paths.path(path).at("/post/responses").properties()).extracting(Map.Entry::getKey).contains("201").doesNotContain("200");
		}
		assertThat(paths.path("/api/auth/refresh").at("/post/responses/200/content/application~1json/schema/$ref").asText()).endsWith("/AuthenticationResponse");
		assertThat(paths.path("/api/auth/me").at("/get/responses/200/content/application~1json/schema/$ref").asText()).endsWith("/CurrentUserResponse");
		assertThat(paths.path("/api/auth/logout").at("/post/responses/204").has("content")).isFalse();
		assertThat(paths.path("/api/auth/csrf").at("/get/responses/204").has("content")).isFalse();
		JsonNode asOf = paths.path("/api/boards/{boardId}/statistics").path("get").path("parameters").get(1);
		assertThat(asOf.path("name").asText()).isEqualTo("asOf");
		assertThat(asOf.path("required").asBoolean()).isTrue();
		assertThat(asOf.at("/schema/format").asText()).isEqualTo("date");
	}

	@Test
	void providesReusableSafeProblemExamplesAndOperationReferences() throws Exception {
		JsonNode api = document();
		Map<String, Integer> expected = Map.ofEntries(
				Map.entry("AUTHENTICATION_REQUIRED", 401), Map.entry("INVALID_CREDENTIALS", 401), Map.entry("SESSION_INVALID", 401),
				Map.entry("BOARD_NOT_FOUND", 404), Map.entry("COLUMN_NOT_FOUND", 404), Map.entry("TASK_NOT_FOUND", 404),
				Map.entry("COLUMN_NOT_EMPTY", 409), Map.entry("COLUMN_ORDER_CONFLICT", 409), Map.entry("TASK_PLACEMENT_CONFLICT", 409),
				Map.entry("VALIDATION_FAILED", 400), Map.entry("MALFORMED_REQUEST", 400), Map.entry("INVALID_SEARCH_QUERY", 400), Map.entry("INTERNAL_ERROR", 500));
		expected.forEach((code, status) -> {
			JsonNode value = api.at("/components/examples/" + code + "/value");
			assertThat(value.path("code").asText()).isEqualTo(code);
			assertThat(value.path("status").asInt()).isEqualTo(status);
			assertThat(value.path("type").asText()).isEqualTo("urn:taskflow:problem:" + code.toLowerCase(java.util.Locale.ROOT));
			assertThat(value.path("title").asText()).isNotBlank();
			assertThat(value.path("detail").asText()).isNotBlank();
			assertThat(value.path("instance").asText()).startsWith("/api/");
			assertThat(value.size()).isEqualTo(code.equals("VALIDATION_FAILED") ? 7 : 6);
		});
		assertThat(api.at("/components/examples/VALIDATION_FAILED/value/violations/0/code").asText()).isEqualTo("NotBlank");
		JsonNode responses = api.path("paths").path("/api/tasks/{taskId}/placement").path("put").path("responses");
		assertThat(responses.path("401").path("$ref").asText()).endsWith("/AuthenticationRequired");
		assertThat(responses.path("404").path("$ref").asText()).endsWith("/PlacementNotFound");
		assertThat(responses.path("400").path("$ref").asText()).endsWith("/InvalidRequest");
		assertThat(responses.path("409").path("$ref").asText()).endsWith("/TaskPlacementConflict");
		assertThat(api.at("/components/responses/InvalidRequest/content/application~1problem+json/schema/$ref").asText()).endsWith("/TaskFlowProblem");
	}

	@Test
	void servesSwaggerRedirectTargetAssetsConfigurationAndYamlAnonymously() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
		mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/v3/api-docs/swagger-config")));
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("TaskFlow API")));
	}

	@Test
	void documentationAccessLeavesBusinessAuthenticationAndCsrfIntact() throws Exception {
		document();
		mvc.perform(get("/api/boards")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		var bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
		var csrf = bootstrap.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		assertThat(bootstrap.getRequest().getSession(false)).isNull();
		mvc.perform(post("/api/boards").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		String bearer = tokens.issue(UUID.randomUUID()).value();
		mvc.perform(post("/api/boards").header("Authorization", "Bearer " + bearer))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mvc.perform(post("/api/auth/login")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}
}
