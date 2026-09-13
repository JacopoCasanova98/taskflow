package com.taskflow.column.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskflow.DatabaseFreePersistenceTest;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.shared.security.jwt.AccessTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ColumnMvcTest.Transactions.class)
class ColumnMvcTest extends DatabaseFreePersistenceTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();
	private static final UUID BOARD = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private AccessTokenService tokens;
	private BoardEntity board;
	private ColumnEntity column;

	@BeforeEach
	void setup() {
		when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
		board = new BoardEntity(A, "Private board");
		ReflectionTestUtils.setField(board, "id", BOARD);
		column = column(ID, "Private column", 0);
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PATCH", "DELETE", "PUT"})
	void allEndpointsRequireBearer(String method) throws Exception {
		var request = request(HttpMethod.valueOf(method), path(method, BOARD, ID)).contentType(MediaType.APPLICATION_JSON);
		if (!method.equals("GET")) {
			Cookie csrf = csrf(); request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
		}
		mvc.perform(request.content(body(method))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(boards, columns);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH", "DELETE", "PUT"})
	void unsafeMethodsRequireValidCsrf(String method) throws Exception {
		String path = path(method, BOARD, ID);
		mvc.perform(authenticated(method, path, A).content(body(method))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mvc.perform(authenticated(method, path, A).cookie(csrf()).header("X-XSRF-TOKEN", "invalid").content(body(method)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		verifyNoInteractions(boards, columns);
	}

	@Test
	void emptyListUsesReadOnlyTransaction() throws Exception {
		owned();
		mvc.perform(authenticated("GET", path("GET", BOARD, ID), A)).andExpect(status().isOk()).andExpect(content().json("[]"));
		assertTransaction(true);
	}

	@Test
	void listPreservesPositionOrderAndExposesExactlySafeFields() throws Exception {
		owned();
		var second = column(UUID.randomUUID(), "Second", 1);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(column, second));
		var response = mvc.perform(authenticated("GET", path("GET", BOARD, ID), A)).andExpect(status().isOk()).andReturn();
		var array = mapper.readTree(response.getResponse().getContentAsString());
		assertThat(array.size()).isEqualTo(2);
		assertSafe(array.get(0), ID, "Private column", 0);
		assertSafe(array.get(1), second.getId(), "Second", 1);
	}

	@Test
	void createReturns201WithoutLocationAndUsesLockedParent() throws Exception {
		owned(); save();
		when(columns.countByBoard_Id(BOARD)).thenReturn(2L);
		var response = mvc.perform(unsafe("POST", path("POST", BOARD, ID), A).content("{\"name\":\"  In   Progress  \"}"))
				.andExpect(status().isCreated()).andExpect(header().doesNotExist("Location")).andReturn();
		assertSafe(mapper.readTree(response.getResponse().getContentAsString()), ID, "In   Progress", 2);
		var entity = ArgumentCaptor.forClass(ColumnEntity.class);
		verify(columns).saveAndFlush(entity.capture());
		assertThat(entity.getValue().getBoard()).isSameAs(board);
		verify(boards).findByIdAndOwnerIdForUpdate(BOARD, A);
		assertTransaction(false);
	}

	@Test
	void renameReturnsSafeBodyAndRetainsBoardAndPosition() throws Exception {
		owned(); save();
		var response = mvc.perform(unsafe("PATCH", path("PATCH", BOARD, ID), A).content("{\"name\":\"  Next   Step  \"}"))
				.andExpect(status().isOk()).andReturn();
		assertSafe(mapper.readTree(response.getResponse().getContentAsString()), ID, "Next   Step", 0);
		assertThat(column.getBoard()).isSameAs(board);
		assertTransaction(false);
	}

	@Test
	void deleteReturns204WithoutBodyAndCompacts() throws Exception {
		owned();
		mvc.perform(unsafe("DELETE", path("DELETE", BOARD, ID), A)).andExpect(status().isNoContent()).andExpect(content().string(""));
		verify(columns).delete(column);
		verify(columns).compactAfterDeletion(eq(BOARD), eq(0), any(Instant.class));
		assertTransaction(false);
	}

	@Test
	void reorderReturnsCanonicalArrayWithinWriteTransaction() throws Exception {
		owned();
		var second = column(UUID.randomUUID(), "Second", 1);
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(column, second));
		var response = mvc.perform(unsafe("PUT", path("PUT", BOARD, ID), A)
				.content(mapper.writeValueAsString(Map.of("columnIds", List.of(second.getId(), ID)))))
				.andExpect(status().isOk()).andReturn();
		var array = mapper.readTree(response.getResponse().getContentAsString());
		assertSafe(array.get(0), second.getId(), "Second", 0);
		assertSafe(array.get(1), ID, "Private column", 1);
		assertTransaction(false);
	}

	@Test
	void emptyOwnedBoardAcceptsEmptyOrder() throws Exception {
		owned();
		mvc.perform(unsafe("PUT", path("PUT", BOARD, ID), A).content(body("PUT")))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
	void otherUsersResourcesAndMissingResourcesAreIndistinguishable(String method) throws Exception {
		owned();
		ObjectNode crossUser = missing(method, BOARD, ID);
		ObjectNode absent = missing(method, UUID.randomUUID(), UUID.randomUUID());
		crossUser.remove("instance"); absent.remove("instance");
		assertThat(crossUser).isEqualTo(absent);
		assertThat(column.getName()).isEqualTo("Private column");
		verify(columns, never()).saveAndFlush(any());
		verify(columns, never()).delete(any());
		verify(columns, never()).flush();
		verify(columns, never()).findById(any());
		verify(boards, never()).findById(any());
	}

	@ParameterizedTest
	@MethodSource("invalidNames")
	void invalidNamesAreValidationFailures(String supplied) throws Exception {
		for (String method : List.of("POST", "PATCH")) {
			mvc.perform(unsafe(method, path(method, BOARD, ID), A).content(supplied)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		verifyNoInteractions(boards, columns);
	}

	@Test
	void normalizedMaximumNameIsAcceptedOnCreateAndRename() throws Exception {
		owned(); save();
		String supplied = mapper.writeValueAsString(Map.of("name", " \t\u2003" + "x".repeat(120) + "  "));
		mvc.perform(unsafe("POST", path("POST", BOARD, ID), A).content(supplied)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("x".repeat(120)));
		mvc.perform(unsafe("PATCH", path("PATCH", BOARD, ID), A).content(supplied)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("x".repeat(120)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"ownerId", "owner", "boardId", "board", "position", "id", "createdAt", "updatedAt"})
	void clientCannotSupplyRelationsPositionOrMetadata(String field) throws Exception {
		for (String method : List.of("POST", "PATCH", "PUT")) {
			ObjectNode supplied = (ObjectNode) mapper.readTree(body(method)); supplied.put(field, B.toString());
			mvc.perform(unsafe(method, path(method, BOARD, ID), A).content(supplied.toString()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		verifyNoInteractions(boards, columns);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"columnIds\":null}", "{\"columnIds\":[null]}"})
	void nullOrderOrElementsAreStructuralValidationFailures(String supplied) throws Exception {
		mvc.perform(unsafe("PUT", path("PUT", BOARD, ID), A).content(supplied)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(boards, columns);
	}

	@Test
	void malformedJsonAndUuidsAreSafeBadRequests() throws Exception {
		for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
			String path = path(method, BOARD, ID).replace(BOARD.toString(), "invalid").replace(ID.toString(), "invalid");
			var request = method.equals("GET") ? authenticated(method, path, A) : unsafe(method, path, A);
			mvc.perform(request.content(body(method))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		for (String method : List.of("POST", "PATCH", "PUT")) {
			mvc.perform(unsafe(method, path(method, BOARD, ID), A).content("{"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		mvc.perform(unsafe("PUT", path("PUT", BOARD, ID), A).content("{\"columnIds\":[\"invalid\"]}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(boards, columns);
	}

	@Test
	void membershipConflictIsSafeAndRollsBack() throws Exception {
		owned();
		when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenReturn(List.of(column));
		UUID foreign = UUID.randomUUID();
		var response = mvc.perform(unsafe("PUT", path("PUT", BOARD, ID), A)
				.content(mapper.writeValueAsString(Map.of("columnIds", List.of(foreign)))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLUMN_ORDER_CONFLICT"))
				.andExpect(jsonPath("$.title").value("Column order conflict"))
				.andExpect(jsonPath("$.detail").value("The submitted column order does not match the board's current columns."))
				.andReturn();
		assertThat(response.getResponse().getContentAsString()).doesNotContain(foreign.toString(), A.toString(), "Private");
		verify(columns, never()).flush();
		verify(transactionManager).rollback(any());
		verify(transactionManager, never()).commit(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH", "DELETE", "PUT"})
	void commitFailureCannotReturnSuccessfulMutation(String method) throws Exception {
		owned(); save();
		doThrow(new DataAccessResourceFailureException("private commit failure")).when(transactionManager).commit(any());
		assertSafe500(unsafe(method, path(method, BOARD, ID), A).content(body(method)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PATCH", "DELETE", "PUT"})
	void persistenceFailureProducesSafe500AndRollback(String method) throws Exception {
		owned();
		var failure = new DataAccessResourceFailureException("private database failure");
		switch (method) {
			case "GET" -> when(columns.findAllByBoard_IdOrderByPositionAscIdAsc(BOARD)).thenThrow(failure);
			case "POST", "PATCH" -> when(columns.saveAndFlush(any())).thenThrow(failure);
			case "DELETE" -> when(columns.compactAfterDeletion(any(), anyInt(), any())).thenThrow(failure);
			case "PUT" -> doThrow(failure).when(columns).flush();
		}
		var request = method.equals("GET") ? authenticated(method, path(method, BOARD, ID), A)
				: unsafe(method, path(method, BOARD, ID), A);
		assertSafe500(request.content(body(method)));
		verify(transactionManager).rollback(any());
		verify(transactionManager, never()).commit(any());
	}

	private void assertSafe500(MockHttpServletRequestBuilder request) throws Exception {
		var result = mvc.perform(request).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private", "ColumnEntity", "ownerId", "BoardEntity");
	}

	private ObjectNode missing(String method, UUID boardId, UUID columnId) throws Exception {
		String path = path(method, boardId, columnId);
		boolean direct = method.equals("PATCH") || method.equals("DELETE");
		var request = method.equals("GET") ? authenticated(method, path, B) : unsafe(method, path, B);
		var result = mvc.perform(request.content(body(method))).andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value(direct ? "COLUMN_NOT_FOUND" : "BOARD_NOT_FOUND"))
				.andExpect(jsonPath("$.title").value(direct ? "Column not found" : "Board not found"))
				.andExpect(jsonPath("$.detail").value(direct ? "The requested column was not found." : "The requested board was not found."))
				.andExpect(jsonPath("$.instance").value(path)).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain(A.toString(), B.toString(), "Private", "ownerId");
		ObjectNode node = (ObjectNode) mapper.readTree(body);
		assertThat(node.size()).isEqualTo(6);
		return node;
	}

	private void owned() {
		when(boards.findByIdAndOwnerId(BOARD, A)).thenReturn(Optional.of(board));
		when(boards.findByIdAndOwnerIdForUpdate(BOARD, A)).thenReturn(Optional.of(board));
		when(columns.findBoardIdByIdAndOwnerId(ID, A)).thenReturn(Optional.of(BOARD));
		when(columns.findByIdAndBoard_OwnerId(ID, A)).thenReturn(Optional.of(column));
	}

	private void save() {
		when(columns.saveAndFlush(any())).thenAnswer(invocation -> {
			ColumnEntity c = invocation.getArgument(0);
			ReflectionTestUtils.setField(c, "id", ID);
			ReflectionTestUtils.setField(c, "createdAt", NOW);
			ReflectionTestUtils.setField(c, "updatedAt", NOW);
			return c;
		});
	}

	private ColumnEntity column(UUID id, String name, int position) {
		var c = new ColumnEntity(board, name, position);
		ReflectionTestUtils.setField(c, "id", id);
		ReflectionTestUtils.setField(c, "createdAt", NOW);
		ReflectionTestUtils.setField(c, "updatedAt", NOW);
		return c;
	}

	private void assertSafe(JsonNode node, UUID id, String name, int position) {
		assertThat(node.size()).isEqualTo(5);
		assertThat(node.get("id").asText()).isEqualTo(id.toString());
		assertThat(node.get("name").asText()).isEqualTo(name);
		assertThat(node.get("position").asInt()).isEqualTo(position);
		assertThat(node.get("createdAt").asText()).isEqualTo(NOW.toString());
		assertThat(node.get("updatedAt").asText()).isEqualTo(NOW.toString());
	}

	private void assertTransaction(boolean readOnly) {
		var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(transactionManager).getTransaction(definition.capture());
		assertThat(definition.getValue().isReadOnly()).isEqualTo(readOnly);
		verify(transactionManager).commit(any());
	}

	private static String path(String method, UUID boardId, UUID columnId) {
		return switch (method) {
			case "PATCH", "DELETE" -> "/api/columns/" + columnId;
			case "PUT" -> "/api/boards/" + boardId + "/columns/order";
			default -> "/api/boards/" + boardId + "/columns";
		};
	}

	private static String body(String method) {
		return method.equals("PUT") ? "{\"columnIds\":[]}" : "{\"name\":\"Name\"}";
	}

	private MockHttpServletRequestBuilder authenticated(String method, String path, UUID owner) {
		return request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + tokens.issue(owner).value())
				.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder unsafe(String method, String path, UUID owner) throws Exception {
		Cookie csrf = csrf();
		return authenticated(method, path, owner).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	private static Stream<String> invalidNames() {
		return Stream.of("{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"  \\t\"}",
				"{\"name\":\"\u2003\"}", "{\"name\":\"" + "x".repeat(121) + "\"}");
	}

	// Real service transaction advice with a mock manager, not a PostgreSQL transaction test.
	@TestConfiguration(proxyBeanMethods = false)
	@EnableTransactionManagement
	static class Transactions {
	}
}
