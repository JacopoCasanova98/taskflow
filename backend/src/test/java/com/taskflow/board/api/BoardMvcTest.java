package com.taskflow.board.api;

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
import com.taskflow.shared.security.jwt.AccessTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BoardMvcTest.Transactions.class)
class BoardMvcTest extends DatabaseFreePersistenceTest {

	private static final UUID OWNER_A = UUID.fromString("7b361e53-9880-477a-9ea1-eac3d57b138b");
	private static final UUID OWNER_B = UUID.fromString("bc6517cf-8914-4818-8b47-37c8f838b657");
	private static final UUID ID = UUID.fromString("5ed6f9a7-a8c9-46c7-b673-d5f1699169f1");
	private static final UUID ABSENT = UUID.fromString("dda0fabe-85a6-49fd-8291-b704bd605f85");
	private static final Instant CREATED = Instant.parse("2026-09-10T10:00:00Z");
	private static final Instant UPDATED = CREATED.plusSeconds(60);
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private AccessTokenService tokens;

	@BeforeEach
	void transactions() {
		when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
	}

	@Test
	void listWithoutBearerRequiresAuthenticationBeforeRepositoryAccess() throws Exception {
		mvc.perform(get("/api/boards")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(boards);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH", "DELETE"})
	void unsafeMethodsRejectMissingAndInvalidCsrfEvenWithValidBearer(String method) throws Exception {
		String path = method.equals("POST") ? "/api/boards" : "/api/boards/" + ID;
		mvc.perform(authenticated(method, path, OWNER_A).content("{\"name\":\"Roadmap\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		Cookie csrf = csrf();
		mvc.perform(authenticated(method, path, OWNER_A).cookie(csrf).header("X-XSRF-TOKEN", "invalid")
				.content("{\"name\":\"Roadmap\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		verifyNoInteractions(boards);
	}

	@Test
	void emptyListIsAnArrayScopedToCurrentUserInAReadOnlyTransaction() throws Exception {
		mvc.perform(authenticated("GET", "/api/boards", OWNER_A)).andExpect(status().isOk())
				.andExpect(content().json("[]"));
		verify(boards).findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER_A);
		verifyNoMoreInteractions(boards);
		assertTransaction(true);
	}

	@Test
	void listPreservesScopedRepositoryOrderingAndReturnsOnlySafeFields() throws Exception {
		var newer = board(ID, "Newer", UPDATED);
		var older = board(ABSENT, "Older", CREATED);
		when(boards.findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER_A)).thenReturn(List.of(newer, older));
		var result = mvc.perform(authenticated("GET", "/api/boards", OWNER_A)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Newer"))
				.andExpect(jsonPath("$[1].name").value("Older")).andReturn();
		JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.size()).isEqualTo(2);
		assertSafeBoard(body.get(0), ID, "Newer", UPDATED, UPDATED);
		assertSafeBoard(body.get(1), ABSENT, "Older", CREATED, CREATED);
		verify(boards).findAllByOwnerIdOrderByCreatedAtDescIdDesc(OWNER_A);
		verifyNoMoreInteractions(boards);
	}

	@Test
	void createsNormalizedEmptyBoardWithCanonicalLocationAndAuthenticatedOwner() throws Exception {
		stubSave();
		var result = mvc.perform(unsafe("POST", "/api/boards", OWNER_A)
				.content(mapper.writeValueAsString(Map.of("name", "  Product   Roadmap  "))))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/boards/" + ID))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
		assertSafeBoard(mapper.readTree(result.getResponse().getContentAsString()), ID, "Product   Roadmap", CREATED, UPDATED);
		var entity = ArgumentCaptor.forClass(BoardEntity.class);
		verify(boards).saveAndFlush(entity.capture());
		assertThat(entity.getValue().getOwnerId()).isEqualTo(OWNER_A);
		assertThat(entity.getValue().getName()).isEqualTo("Product   Roadmap");
		verifyNoMoreInteractions(boards);
		verifyNoInteractions(users, sessions);
		assertTransaction(false);
	}

	@ParameterizedTest
	@ValueSource(strings = {"ownerId", "owner", "id", "createdAt", "updatedAt"})
	void clientSuppliedOwnershipAndMetadataAreRejectedOnCreationAndRename(String field) throws Exception {
		String supplied = mapper.writeValueAsString(Map.of("name", "Roadmap", field, OWNER_B.toString()));
		for (String method : List.of("POST", "PATCH")) {
			String path = method.equals("POST") ? "/api/boards" : "/api/boards/" + ID;
			mvc.perform(unsafe(method, path, OWNER_A).content(supplied))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		}
		verifyNoInteractions(boards);
	}

	@Test
	void readsOwnedBoardWithSafeBodyInReadOnlyTransaction() throws Exception {
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenReturn(Optional.of(board(ID, "Roadmap", CREATED)));
		var result = mvc.perform(authenticated("GET", "/api/boards/" + ID, OWNER_A))
				.andExpect(status().isOk()).andReturn();
		assertSafeBoard(mapper.readTree(result.getResponse().getContentAsString()), ID, "Roadmap", CREATED, CREATED);
		verify(boards).findByIdAndOwnerId(ID, OWNER_A);
		verifyNoMoreInteractions(boards);
		assertTransaction(true);
	}

	@Test
	void renamesOwnedBoardAndReturnsFlushedTimestamp() throws Exception {
		var existing = board(ID, "Original", CREATED);
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenReturn(Optional.of(existing));
		stubSave();
		var result = mvc.perform(unsafe("PATCH", "/api/boards/" + ID, OWNER_A)
				.content("{\"name\":\"  Next   Roadmap  \"}"))
				.andExpect(status().isOk()).andReturn();
		assertSafeBoard(mapper.readTree(result.getResponse().getContentAsString()), ID, "Next   Roadmap", CREATED, UPDATED);
		assertThat(existing.getOwnerId()).isEqualTo(OWNER_A);
		verify(boards).findByIdAndOwnerId(ID, OWNER_A);
		verify(boards).saveAndFlush(existing);
		verifyNoMoreInteractions(boards);
		assertTransaction(false);
	}

	@Test
	void deletesOwnedBoardWithNoBody() throws Exception {
		var existing = board(ID, "Roadmap", CREATED);
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenReturn(Optional.of(existing));
		mvc.perform(unsafe("DELETE", "/api/boards/" + ID, OWNER_A))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		var order = inOrder(boards);
		order.verify(boards).findByIdAndOwnerId(ID, OWNER_A);
		order.verify(boards).delete(existing);
		verifyNoMoreInteractions(boards);
		assertTransaction(false);
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "PATCH", "DELETE"})
	void userBReceivesIdenticalNotFoundForUserABoardAndMissingBoard(String method) throws Exception {
		var ownedByA = board(ID, "Private", CREATED);
		// Model the scoped repository contract, not PostgreSQL execution or row-level security.
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenReturn(Optional.of(ownedByA));
		mvc.perform(authenticated("GET", "/api/boards/" + ID, OWNER_A)).andExpect(status().isOk());
		ObjectNode crossUser = notFound(method, ID);
		ObjectNode missing = notFound(method, ABSENT);
		// Only instance differs because the requested resource paths differ.
		crossUser.remove("instance");
		missing.remove("instance");
		assertThat(crossUser).isEqualTo(missing);
		assertThat(ownedByA.getName()).isEqualTo("Private");
		verify(boards).findByIdAndOwnerId(ID, OWNER_A);
		verify(boards).findByIdAndOwnerId(ID, OWNER_B);
		verify(boards).findByIdAndOwnerId(ABSENT, OWNER_B);
		verifyNoMoreInteractions(boards);
	}

	@ParameterizedTest
	@MethodSource("invalidNames")
	void createAndRenameRejectInvalidNamesBeforePersistence(String body) throws Exception {
		for (String method : List.of("POST", "PATCH")) {
			String path = method.equals("POST") ? "/api/boards" : "/api/boards/" + ID;
			mvc.perform(unsafe(method, path, OWNER_A).content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.violations[0].field").value("name"));
		}
		verifyNoInteractions(boards);
	}

	@Test
	void normalizedMaximumLengthIsAcceptedOnCreateAndRename() throws Exception {
		stubSave();
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenReturn(Optional.of(board(ID, "Original", CREATED)));
		String maximum = "x".repeat(120);
		String body = mapper.writeValueAsString(Map.of("name", " \t\u2003" + maximum + "\u2003  "));
		mvc.perform(unsafe("POST", "/api/boards", OWNER_A).content(body)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(maximum));
		mvc.perform(unsafe("PATCH", "/api/boards/" + ID, OWNER_A).content(body)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(maximum));
	}

	@Test
	void malformedIdAndJsonAreSafeBadRequests() throws Exception {
		mvc.perform(authenticated("GET", "/api/boards/not-a-uuid", OWNER_A)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		mvc.perform(unsafe("POST", "/api/boards", OWNER_A).content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(boards);
	}

	@Test
	void databaseFailureIsSafe500RatherThanNotFound() throws Exception {
		when(boards.findByIdAndOwnerId(ID, OWNER_A)).thenThrow(new DataAccessResourceFailureException("private database details"));
		var result = mvc.perform(authenticated("GET", "/api/boards/" + ID, OWNER_A))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private database details", "BoardEntity", "ownerId");
	}

	@Test
	void failedTransactionCommitCannotReturnSuccessfulCreation() throws Exception {
		stubSave();
		doThrow(new DataAccessResourceFailureException("private commit failure")).when(transactionManager).commit(any());
		var result = mvc.perform(unsafe("POST", "/api/boards", OWNER_A).content("{\"name\":\"Roadmap\"}"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(header().doesNotExist("Location")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private commit failure", "ownerId");
	}

	private ObjectNode notFound(String method, UUID id) throws Exception {
		String path = "/api/boards/" + id;
		var request = method.equals("GET") ? authenticated(method, path, OWNER_B) : unsafe(method, path, OWNER_B);
		MvcResult result = mvc.perform(request.content("{\"name\":\"Changed\"}"))
				.andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:taskflow:problem:board_not_found"))
				.andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"))
				.andExpect(jsonPath("$.title").value("Board not found"))
				.andExpect(jsonPath("$.detail").value("The requested board was not found."))
				.andExpect(jsonPath("$.instance").value(path)).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain(OWNER_A.toString(), OWNER_B.toString(), "Private", "ownerId");
		ObjectNode node = (ObjectNode) mapper.readTree(body);
		assertThat(node.size()).isEqualTo(6);
		return node;
	}

	private void assertSafeBoard(JsonNode body, UUID id, String name, Instant created, Instant updated) {
		assertThat(body.size()).isEqualTo(4);
		assertThat(body.get("id").asText()).isEqualTo(id.toString());
		assertThat(body.get("name").asText()).isEqualTo(name);
		assertThat(body.get("createdAt").asText()).isEqualTo(created.toString());
		assertThat(body.get("updatedAt").asText()).isEqualTo(updated.toString());
	}

	private void assertTransaction(boolean readOnly) {
		var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(transactionManager).getTransaction(definition.capture());
		assertThat(definition.getValue().isReadOnly()).isEqualTo(readOnly);
		verify(transactionManager).commit(any());
	}

	private void stubSave() {
		when(boards.saveAndFlush(any(BoardEntity.class))).thenAnswer(invocation -> {
			BoardEntity board = invocation.getArgument(0);
			// Stand in for JPA-generated identity and audit fields; no database executes here.
			ReflectionTestUtils.setField(board, "id", ID);
			ReflectionTestUtils.setField(board, "createdAt", CREATED);
			ReflectionTestUtils.setField(board, "updatedAt", UPDATED);
			return board;
		});
	}

	private MockHttpServletRequestBuilder authenticated(String method, String path, UUID owner) {
		return request(HttpMethod.valueOf(method), path)
				.header("Authorization", "Bearer " + tokens.issue(owner).value()).contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder unsafe(String method, String path, UUID owner) throws Exception {
		Cookie csrf = csrf();
		return authenticated(method, path, owner).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	private static Stream<String> invalidNames() {
		return Stream.of("{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"  \\t\\n  \"}",
				"{\"name\":\"\u2003\"}", "{\"name\":\"" + "x".repeat(121) + "\"}");
	}

	private static BoardEntity board(UUID id, String name, Instant created) {
		var board = new BoardEntity(OWNER_A, name);
		ReflectionTestUtils.setField(board, "id", id);
		ReflectionTestUtils.setField(board, "createdAt", created);
		ReflectionTestUtils.setField(board, "updatedAt", created);
		return board;
	}

	// The database-free profile has no JPA auto-configuration to enable transaction interception.
	// Exercise real service transaction advice against the existing mock manager explicitly.
	@TestConfiguration(proxyBeanMethods = false)
	@EnableTransactionManagement
	static class Transactions {
	}
}
