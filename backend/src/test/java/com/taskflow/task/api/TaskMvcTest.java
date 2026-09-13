package com.taskflow.task.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDate;
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
import com.taskflow.task.persistence.TaskEntity;
import com.taskflow.task.domain.TaskPriority;
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
@Import(TaskMvcTest.Transactions.class)
class TaskMvcTest extends DatabaseFreePersistenceTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();
	private static final UUID BOARD = UUID.randomUUID();
	private static final UUID COLUMN = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private AccessTokenService tokens;
	private BoardEntity board;
	private ColumnEntity column;
	private TaskEntity task;

	@BeforeEach
	void setup() {
		when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
		board = new BoardEntity(A, "Private board");
		ReflectionTestUtils.setField(board, "id", BOARD);
		column = new ColumnEntity(board, "Private column", 0);
		ReflectionTestUtils.setField(column, "id", COLUMN);
		task = new TaskEntity(column, "Private task", "Description", TaskPriority.LOW, LocalDate.of(2026, 9, 15), 0);
		metadata(task);
	}
	@ParameterizedTest
	@ValueSource(strings = {"list", "create", "get", "update", "delete", "place"})
	void everyEndpointRequiresAuthentication(String op) throws Exception {
		var req = request(HttpMethod.valueOf(method(op)), path(op, COLUMN, ID)).contentType(MediaType.APPLICATION_JSON);
		if (!method(op).equals("GET")) {
			Cookie csrf = csrf(); req.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
		}
		mvc.perform(req.content(body(op))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(tasks, columns, boards);
	}
	@ParameterizedTest
	@ValueSource(strings = {"create", "update", "delete", "place"})
	void unsafeEndpointsRequireValidCsrf(String op) throws Exception {
		mvc.perform(authenticated(method(op), path(op, COLUMN, ID), A).content(body(op)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mvc.perform(authenticated(method(op), path(op, COLUMN, ID), A).cookie(csrf())
				.header("X-XSRF-TOKEN", "invalid").content(body(op)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		verifyNoInteractions(tasks, columns, boards);
	}
	@Test
	void ownedEmptyColumnReturnsEmptyArrayInReadOnlyTransaction() throws Exception {
		owned();
		mvc.perform(call("list", A)).andExpect(status().isOk()).andExpect(content().json("[]"));
		assertTransaction(true);
	}
	@Test
	void listReturnsOrderedSafeTaskArray() throws Exception {
		owned();
		var second = new TaskEntity(column, "Second", null, null, null, 1);
		metadata(second); ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(COLUMN, A)).thenReturn(List.of(task, second));
		var response = mvc.perform(call("list", A)).andExpect(status().isOk()).andReturn();
		var array = mapper.readTree(response.getResponse().getContentAsString());
		assertThat(array.size()).isEqualTo(2);
		assertSafe(array.get(0), ID, COLUMN, "Private task", 0);
		assertSafe(array.get(1), second.getId(), COLUMN, "Second", 1);
	}
	@ParameterizedTest
	@ValueSource(strings = {"omitted", "null", "LOW", "MEDIUM", "HIGH"})
	void createDefaultsOrRetainsPriorityAndReturnsCanonicalLocation(String priority) throws Exception {
		owned(); save();
		ObjectNode supplied = mapper.createObjectNode().put("title", "  Fix   Login  ");
		if (priority.equals("null")) supplied.putNull("priority");
		else if (!priority.equals("omitted")) supplied.put("priority", priority);
		var response = mvc.perform(call("create", A).content(supplied.toString()))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tasks/" + ID)).andReturn();
		var json = mapper.readTree(response.getResponse().getContentAsString());
		assertSafe(json, ID, COLUMN, "Fix   Login", 0);
		assertThat(json.get("priority").asText()).isEqualTo(List.of("omitted", "null").contains(priority) ? "MEDIUM" : priority);
		assertThat(json.get("description").isNull()).isTrue();
		assertThat(json.get("dueDate").isNull()).isTrue();
		assertTransaction(false);
	}
	@Test
	void createAppendsAndRetainsDescriptionAndDate() throws Exception {
		owned(); save();
		var second = new TaskEntity(column, "Second", null, null, null, 1);
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(COLUMN, A)).thenReturn(List.of(task, second));
		String supplied = mapper.writeValueAsString(Map.of("title", "New", "description", " First  line\nSecond line ",
				"priority", "HIGH", "dueDate", "2026-12-31"));
		mvc.perform(call("create", A).content(supplied)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.position").value(2))
				.andExpect(jsonPath("$.description").value("First  line\nSecond line"))
				.andExpect(jsonPath("$.dueDate").value("2026-12-31"));
	}

	@Test
	void getReturnsSafeTaskInReadOnlyTransaction() throws Exception {
		owned();
		var response = mvc.perform(call("get", A)).andExpect(status().isOk()).andReturn();
		assertSafe(mapper.readTree(response.getResponse().getContentAsString()), ID, COLUMN, "Private task", 0);
		assertTransaction(true);
	}
	@Test
	void updateReplacesContentAndRetainsPlacement() throws Exception {
		owned(); save();
		var response = mvc.perform(call("update", A).content("""
				{"title":" New ","description":" First line\nSecond line ","priority":"HIGH","dueDate":"2026-10-01"}
				""".replace("line\nSecond", "line\\nSecond")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.description").value("First line\nSecond line"))
				.andExpect(jsonPath("$.priority").value("HIGH")).andExpect(jsonPath("$.dueDate").value("2026-10-01")).andReturn();
		assertSafe(mapper.readTree(response.getResponse().getContentAsString()), ID, COLUMN, "New", 0);
		assertThat(task.getColumn()).isSameAs(column);
		assertTransaction(false);
	}
	@Test
	void updateNullsClearDescriptionAndDate() throws Exception {
		owned(); save();
		mvc.perform(call("update", A).content("{\"title\":\"Title\",\"description\":null,\"priority\":\"MEDIUM\",\"dueDate\":null}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.dueDate").isEmpty());
		assertThat(task.getDescription()).isNull(); assertThat(task.getDueDate()).isNull();
	}
	@Test
	void deleteReturnsNoContentAndFlushesInWriteTransaction() throws Exception {
		owned();
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(COLUMN, A)).thenReturn(List.of(task));
		mvc.perform(call("delete", A)).andExpect(status().isNoContent()).andExpect(content().string(""));
		verify(tasks).delete(task); verify(tasks).flush(); assertTransaction(false);
	}
	@Test
	void placementReturnsMovedTaskInWriteTransaction() throws Exception {
		owned();
		UUID targetId = UUID.randomUUID();
		var target = new ColumnEntity(board, "Target", 1); ReflectionTestUtils.setField(target, "id", targetId);
		when(columns.findByIdAndBoard_IdAndBoard_OwnerId(targetId, BOARD, A)).thenReturn(Optional.of(target));
		when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(COLUMN, A)).thenReturn(List.of(task));
		var response = mvc.perform(call("place", A).content(mapper.writeValueAsString(Map.of("columnId", targetId, "position", 0))))
				.andExpect(status().isOk()).andReturn();
		assertSafe(mapper.readTree(response.getResponse().getContentAsString()), ID, targetId, "Private task", 0);
		verify(tasks).flush(); assertTransaction(false);
	}
	@ParameterizedTest
	@ValueSource(strings = {"list", "create", "get", "update", "delete", "place"})
	void crossUserAndMissingResourcesHaveIdenticalPublicResponses(String op) throws Exception {
		owned();
		ObjectNode foreign = missing(op, COLUMN, ID);
		ObjectNode absent = missing(op, UUID.randomUUID(), UUID.randomUUID());
		foreign.remove("instance"); absent.remove("instance"); assertThat(foreign).isEqualTo(absent);
		assertThat(task.getTitle()).isEqualTo("Private task");
		verify(tasks, never()).saveAndFlush(any()); verify(tasks, never()).delete(any()); verify(tasks, never()).flush();
		verify(tasks, never()).findById(any()); verify(columns, never()).findById(any());
	}
	@Test
	void invalidPlacementReturnsSafeConflictAndRollsBack() throws Exception {
		owned();
		mvc.perform(call("place", A).content(mapper.writeValueAsString(Map.of("columnId", COLUMN, "position", 1))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_PLACEMENT_CONFLICT"))
				.andExpect(jsonPath("$.title").value("Task placement conflict"))
				.andExpect(jsonPath("$.detail").value("The requested task position is not valid for the target column."));
		verify(tasks, never()).flush(); verify(transactionManager).rollback(any()); verify(transactionManager, never()).commit(any());
	}
	@ParameterizedTest
	@ValueSource(strings = {"missing", "otherBoard", "otherUser"})
	void unavailableTargetReturnsSafeColumnNotFound(String scenario) throws Exception {
		owned(); UUID targetId = UUID.randomUUID();
		if (!scenario.equals("missing")) {
			when(columns.findByIdAndBoard_IdAndBoard_OwnerId(targetId, UUID.randomUUID(), scenario.equals("otherUser") ? B : A))
					.thenReturn(Optional.of(column));
		}
		mvc.perform(call("place", A).content(mapper.writeValueAsString(Map.of("columnId", targetId, "position", 0))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COLUMN_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("The requested column was not found."));
		verify(tasks, never()).flush();
	}
	@ParameterizedTest
	@MethodSource("invalidContent")
	void invalidContentReturnsValidationFailed(String supplied) throws Exception {
		for (String op : List.of("create", "update")) {
			mvc.perform(call(op, A).content(supplied)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		verifyNoInteractions(tasks, columns, boards);
	}
	@ParameterizedTest
	@ValueSource(strings = {"{\"title\":\"Title\"}", "{\"title\":\"Title\",\"priority\":null}"})
	void updateRequiresExplicitPriority(String supplied) throws Exception {
		mvc.perform(call("update", A).content(supplied)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(tasks, columns, boards);
	}
	@Test
	void normalizedMaximumContentAccepted() throws Exception {
		owned(); save();
		String supplied = mapper.writeValueAsString(Map.of("title", " " + "x".repeat(200) + " ",
				"description", " " + "y".repeat(4000) + " ", "priority", "HIGH"));
		mvc.perform(call("create", A).content(supplied)).andExpect(status().isCreated());
		mvc.perform(call("update", A).content(supplied)).andExpect(status().isOk());
	}
	@ParameterizedTest
	@ValueSource(strings = {"ownerId", "owner", "boardId", "board", "columnId", "column", "position", "id", "createdAt", "updatedAt", "status"})
	void createAndUpdateRejectMetadataAndPlacementFields(String field) throws Exception {
		for (String op : List.of("create", "update")) {
			ObjectNode supplied = (ObjectNode) mapper.readTree(body(op)); supplied.put(field, B.toString());
			mvc.perform(call(op, A).content(supplied.toString())).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		verifyNoInteractions(tasks, columns, boards);
	}
	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"columnId\":null,\"position\":0}", "{\"position\":0}"})
	void placementRequiresColumn(String supplied) throws Exception {
		mvc.perform(call("place", A).content(supplied)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
	@ParameterizedTest
	@ValueSource(strings = {"omitted", "null", "-1"})
	void placementRequiresNonNegativePosition(String position) throws Exception {
		String supplied = "{\"columnId\":\"" + COLUMN + "\"" + (position.equals("omitted") ? "" : ",\"position\":" + position) + "}";
		mvc.perform(call("place", A).content(supplied)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(tasks, columns, boards);
	}
	@ParameterizedTest
	@ValueSource(strings = {"{", "{\"title\":\"T\",\"priority\":0}", "{\"title\":\"T\",\"priority\":\"URGENT\"}",
			"{\"title\":\"T\",\"priority\":\"low\"}", "{\"title\":\"T\",\"priority\":\"HIGH\",\"dueDate\":\"bad\"}",
			"{\"title\":\"T\",\"priority\":\"HIGH\",\"dueDate\":\"2026-13-01\"}"})
	void malformedContentIsSafeBadRequest(String supplied) throws Exception {
		for (String op : List.of("create", "update")) mvc.perform(call(op, A).content(supplied))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(tasks, columns, boards);
	}
	@Test
	void malformedUuidsAndPlacementJsonAreRejected() throws Exception {
		for (String op : List.of("list", "create", "get", "update", "delete", "place")) {
			String path = path(op, COLUMN, ID).replace(COLUMN.toString(), "bad").replace(ID.toString(), "bad");
			mvc.perform(secured(method(op), path, A).content(body(op))).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		for (String supplied : List.of("{", "{\"columnId\":\"bad\",\"position\":0}",
				"{\"columnId\":\"" + COLUMN + "\",\"position\":0,\"title\":\"T\"}")) {
			mvc.perform(call("place", A).content(supplied)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
	}
	@Test
	void nonEmptyColumnDeletionReturnsConflictWithoutChangingColumnOrTasks() throws Exception {
		owned(); when(tasks.existsByColumn_Id(COLUMN)).thenReturn(true);
		mvc.perform(secured("DELETE", "/api/columns/" + COLUMN, A)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COLUMN_NOT_EMPTY"))
				.andExpect(jsonPath("$.title").value("Column not empty"))
				.andExpect(jsonPath("$.detail").value("The column must be empty before it can be deleted."));
		var order = inOrder(columns, boards, tasks);
		order.verify(columns).findBoardIdByIdAndOwnerId(COLUMN, A);
		order.verify(boards).findByIdAndOwnerIdForUpdate(BOARD, A);
		order.verify(columns).findByIdAndBoard_OwnerId(COLUMN, A);
		order.verify(tasks).existsByColumn_Id(COLUMN);
		verify(columns, never()).delete(any()); verify(columns, never()).compactAfterDeletion(any(), anyInt(), any());
		verify(tasks, never()).delete(any());
		assertThat(column.getPosition()).isZero(); assertThat(task.getColumn()).isSameAs(column);
		verify(transactionManager).rollback(any());
	}
	@ParameterizedTest
	@ValueSource(strings = {"create", "update", "delete", "place"})
	void commitFailureCannotReturnSuccessfulMutation(String op) throws Exception {
		owned(); save();
		doThrow(new DataAccessResourceFailureException("private commit failure")).when(transactionManager).commit(any());
		safe500(call(op, A));
	}
	@ParameterizedTest
	@ValueSource(strings = {"list", "create", "get", "update", "delete", "place"})
	void repositoryFailureReturnsSafe500AndRollsBack(String op) throws Exception {
		owned(); var failure = new DataAccessResourceFailureException("private database failure");
		switch (op) {
			case "list" -> when(tasks.findAllByColumn_IdAndColumn_Board_OwnerIdOrderByPositionAscIdAsc(COLUMN, A)).thenThrow(failure);
			case "get" -> when(tasks.findByIdAndColumn_Board_OwnerId(ID, A)).thenThrow(failure);
			case "create", "update" -> when(tasks.saveAndFlush(any())).thenThrow(failure);
			case "delete", "place" -> doThrow(failure).when(tasks).flush();
		}
		safe500(call(op, A)); verify(transactionManager).rollback(any()); verify(transactionManager, never()).commit(any());
	}
	private void safe500(MockHttpServletRequestBuilder request) throws Exception {
		var response = mvc.perform(request).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(response.getResponse().getContentAsString()).doesNotContain("private", "TaskEntity", "ColumnEntity", "ownerId", "BoardEntity");
	}
	private ObjectNode missing(String op, UUID columnId, UUID taskId) throws Exception {
		boolean parent = List.of("list", "create").contains(op);
		String path = path(op, columnId, taskId);
		var response = mvc.perform(secured(method(op), path, B).content(body(op))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(parent ? "COLUMN_NOT_FOUND" : "TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.title").value(parent ? "Column not found" : "Task not found"))
				.andExpect(jsonPath("$.detail").value(parent ? "The requested column was not found." : "The requested task was not found."))
				.andExpect(jsonPath("$.instance").value(path)).andReturn();
		String body = response.getResponse().getContentAsString();
		assertThat(body).doesNotContain(A.toString(), B.toString(), "Private", "ownerId");
		return (ObjectNode) mapper.readTree(body);
	}
	private void owned() {
		when(boards.findByIdAndOwnerIdForUpdate(BOARD, A)).thenReturn(Optional.of(board));
		when(columns.findBoardIdByIdAndOwnerId(COLUMN, A)).thenReturn(Optional.of(BOARD));
		when(columns.findByIdAndBoard_OwnerId(COLUMN, A)).thenReturn(Optional.of(column));
		when(columns.findByIdAndBoard_IdAndBoard_OwnerId(COLUMN, BOARD, A)).thenReturn(Optional.of(column));
		when(tasks.findBoardIdByIdAndOwnerId(ID, A)).thenReturn(Optional.of(BOARD));
		when(tasks.findByIdAndColumn_Board_OwnerId(ID, A)).thenReturn(Optional.of(task));
		when(tasks.findByIdAndColumn_Board_IdAndColumn_Board_OwnerId(ID, BOARD, A)).thenReturn(Optional.of(task));
	}
	private void save() {
		when(tasks.saveAndFlush(any())).thenAnswer(inv -> { TaskEntity t = inv.getArgument(0); metadata(t); return t; });
	}
	private static void metadata(TaskEntity task) {
		ReflectionTestUtils.setField(task, "id", ID);
		ReflectionTestUtils.setField(task, "createdAt", NOW);
		ReflectionTestUtils.setField(task, "updatedAt", NOW);
	}
	private void assertSafe(JsonNode node, UUID id, UUID columnId, String title, int position) {
		assertThat(node.size()).isEqualTo(9);
		assertThat(node.get("id").asText()).isEqualTo(id.toString());
		assertThat(node.get("columnId").asText()).isEqualTo(columnId.toString());
		assertThat(node.get("title").asText()).isEqualTo(title);
		assertThat(node.get("position").asInt()).isEqualTo(position);
		assertThat(node.has("description")).isTrue(); assertThat(node.has("priority")).isTrue(); assertThat(node.has("dueDate")).isTrue();
		assertThat(node.get("createdAt").asText()).isEqualTo(NOW.toString());
		assertThat(node.get("updatedAt").asText()).isEqualTo(NOW.toString());
	}
	private void assertTransaction(boolean readOnly) {
		var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(transactionManager).getTransaction(definition.capture());
		assertThat(definition.getValue().isReadOnly()).isEqualTo(readOnly);
		verify(transactionManager).commit(any());
	}
	private MockHttpServletRequestBuilder call(String op, UUID owner) throws Exception {
		return secured(method(op), path(op, COLUMN, ID), owner).content(body(op));
	}
	private MockHttpServletRequestBuilder secured(String method, String path, UUID owner) throws Exception {
		var req = authenticated(method, path, owner);
		if (!method.equals("GET")) { Cookie csrf = csrf(); req.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()); }
		return req;
	}
	private MockHttpServletRequestBuilder authenticated(String method, String path, UUID owner) {
		return request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + tokens.issue(owner).value())
				.contentType(MediaType.APPLICATION_JSON);
	}
	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}
	@Test
	void searchIsAuthenticatedSafeReadWithoutCsrfAndReturnsOnlyTaskResponse() throws Exception {
		String path = "/api/boards/" + BOARD + "/tasks/search";
		mvc.perform(get(path).param("q", "login")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		when(boards.findByIdAndOwnerId(BOARD, A)).thenReturn(Optional.of(board));
		when(tasks.searchBoardTasks(BOARD, A, "%login%")).thenReturn(List.of(task));
		mvc.perform(authenticated("GET", path, A).param("q", " login "))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].description").value("Description"))
				.andExpect(jsonPath("$[0].ownerId").doesNotExist()).andExpect(jsonPath("$[0].column").doesNotExist())
				.andExpect(jsonPath("$[0].board").doesNotExist());
		mvc.perform(authenticated("GET", path, A).param("q", "  "))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}
	@Test
	void searchMissingAndCrossUserBoardUseSameSafe404() throws Exception {
		when(boards.findByIdAndOwnerId(BOARD, A)).thenReturn(Optional.of(board));
		for (UUID id : List.of(BOARD, UUID.randomUUID()))
			mvc.perform(authenticated("GET", "/api/boards/" + id + "/tasks/search", B).param("q", ""))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"))
					.andExpect(jsonPath("$.detail").value("The requested board was not found."));
		verifyNoInteractions(tasks);
	}
	@Test
	void searchInvalidQueryMalformedIdAndUnexpectedErrorStaySafe() throws Exception {
		String path = "/api/boards/" + BOARD + "/tasks/search";
		when(boards.findByIdAndOwnerId(BOARD, A)).thenReturn(Optional.of(board));
		mvc.perform(authenticated("GET", path, A).param("q", "x".repeat(201)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
		mvc.perform(authenticated("GET", "/api/boards/invalid/tasks/search", A).param("q", "login"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		when(tasks.searchBoardTasks(BOARD, A, "%login%")).thenThrow(new IllegalStateException("private SQL"));
		var response = mvc.perform(authenticated("GET", path, A).param("q", "login"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain("private SQL", "IllegalStateException");
	}

	private static String method(String op) {
		return switch (op) { case "create" -> "POST"; case "update", "place" -> "PUT"; case "delete" -> "DELETE"; default -> "GET"; };
	}
	private static String path(String op, UUID column, UUID task) {
		return switch (op) { case "list", "create" -> "/api/columns/" + column + "/tasks";
			case "place" -> "/api/tasks/" + task + "/placement"; default -> "/api/tasks/" + task; };
	}
	private static String body(String op) {
		return op.equals("place") ? "{\"columnId\":\"" + COLUMN + "\",\"position\":0}" : "{\"title\":\"Title\",\"priority\":\"MEDIUM\"}";
	}
	private static Stream<String> invalidContent() {
		return Stream.of("{}", "{\"title\":null,\"priority\":\"HIGH\"}", "{\"title\":\" \u2003\",\"priority\":\"HIGH\"}",
				"{\"title\":\"" + "x".repeat(201) + "\",\"priority\":\"HIGH\"}",
				"{\"title\":\"T\",\"priority\":\"HIGH\",\"description\":\"" + "x".repeat(4001) + "\"}");
	}
	// Real service advice, mock transaction manager: not a PostgreSQL transaction test.
	@TestConfiguration(proxyBeanMethods = false)
	@EnableTransactionManagement
	static class Transactions {}
}
