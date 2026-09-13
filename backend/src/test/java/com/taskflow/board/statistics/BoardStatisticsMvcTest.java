package com.taskflow.board.statistics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.task.domain.TaskPriority;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskflow.DatabaseFreePersistenceTest;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.task.persistence.TaskRepository;
import com.taskflow.shared.security.jwt.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BoardStatisticsMvcTest.Transactions.class)
class BoardStatisticsMvcTest extends DatabaseFreePersistenceTest {
	private static final UUID OWNER = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final LocalDate DAY = LocalDate.of(2026, 9, 13);
	private static final String PATH = "/api/boards/" + ID + "/statistics";
	@Autowired private MockMvc mvc;
	@Autowired private AccessTokenService tokens;
	@Autowired private ObjectMapper mapper;

	@BeforeEach
	void setup() {
		when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
	}
	@Test
	void anonymousIsRejectedBeforeRepositoryAccess() throws Exception {
		mvc.perform(get(PATH).param("asOf", DAY.toString())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(boards, tasks, columns);
	}
	@Test
	void getWithoutCsrfReturnsExactSafeZeroContractInReadOnlyTransaction() throws Exception {
		owned();
		var result = mvc.perform(request(PATH, OWNER).param("asOf", DAY.toString())).andExpect(status().isOk()).andReturn();
		assertThat(mapper.readTree(result.getResponse().getContentAsString())).isEqualTo(mapper.readTree("""
				{"totalTasks":0,"overdueTasks":0,"priorityDistribution":{"low":0,"medium":0,"high":0},"statusDistribution":[]}
				"""));
		verify(tasks).aggregateBoardTotals(ID, OWNER, DAY);
		var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
		verify(transactionManager).getTransaction(definition.capture());
		assertThat(definition.getValue().isReadOnly()).isTrue();
		verify(transactionManager).commit(any());
	}
	@Test
	void returnsExactNonzeroSafeContractAndAnEmptyUserDefinedDoneColumn() throws Exception {
		owned();
		var totals = mock(TaskRepository.Totals.class);
		when(totals.getTotal()).thenReturn(4L);
		when(totals.getOverdue()).thenReturn(1L);
		when(tasks.aggregateBoardTotals(ID, OWNER, DAY)).thenReturn(totals);
		var high = mock(TaskRepository.PriorityCount.class);
		when(high.getPriority()).thenReturn(TaskPriority.HIGH);
		when(high.getTaskCount()).thenReturn(4L);
		when(tasks.aggregateBoardPriorities(ID, OWNER)).thenReturn(List.of(high));
		var backlog = new ColumnEntity(new BoardEntity(OWNER, "Private"), "Backlog", 0);
		var done = new ColumnEntity(new BoardEntity(OWNER, "Private"), "Done", 1);
		ReflectionTestUtils.setField(backlog, "id", ID);
		ReflectionTestUtils.setField(done, "id", OTHER);
		when(columns.findAllByBoard_IdAndBoard_OwnerIdOrderByPositionAscIdAsc(ID, OWNER)).thenReturn(List.of(backlog, done));
		var count = mock(TaskRepository.ColumnCount.class);
		when(count.getColumnId()).thenReturn(ID);
		when(count.getTaskCount()).thenReturn(4L);
		when(tasks.aggregateBoardColumns(ID, OWNER)).thenReturn(List.of(count));
		var result = mvc.perform(request(PATH, OWNER).param("asOf", DAY.toString())).andExpect(status().isOk()).andReturn();
		assertThat(mapper.readTree(result.getResponse().getContentAsString())).isEqualTo(mapper.readTree("""
				{"totalTasks":4,"overdueTasks":1,"priorityDistribution":{"low":0,"medium":0,"high":4},
				"statusDistribution":[{"columnId":"%s","name":"Backlog","position":0,"taskCount":4},
				{"columnId":"%s","name":"Done","position":1,"taskCount":0}]}
				""".formatted(ID, OTHER)));
	}
	@Test
	void missingAndOtherUsersBoardAreIndistinguishable() throws Exception {
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(new BoardEntity(OWNER, "Private")));
		ObjectNode cross = notFound(PATH);
		ObjectNode missing = notFound("/api/boards/" + UUID.randomUUID() + "/statistics");
		cross.remove("instance");
		missing.remove("instance");
		assertThat(cross).isEqualTo(missing);
		verifyNoInteractions(tasks, columns);
	}
	@Test
	void missingAsOfIsSafe400() throws Exception {
		mvc.perform(request(PATH, OWNER)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(boards, tasks, columns);
	}
	@ParameterizedTest
	@ValueSource(strings = {"", "tomorrow", "2026-9-13", "13/09/2026", "2026-02-30", "2026-09-13T00:00:00", "2026-09-13Z", "2026-09-13+02:00"})
	void malformedAsOfIsSafe400(String date) throws Exception {
		mvc.perform(request(PATH, OWNER).param("asOf", date)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(boards, tasks, columns);
	}
	@Test
	void malformedUuidIsSafe400() throws Exception {
		mvc.perform(request("/api/boards/not-a-uuid/statistics", OWNER).param("asOf", DAY.toString()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		verifyNoInteractions(boards, tasks, columns);
	}
	@Test
	void unexpectedAggregationFailureHasSafe500() throws Exception {
		owned();
		when(tasks.aggregateBoardTotals(ID, OWNER, DAY)).thenThrow(new IllegalStateException("secret SQL owner"));
		var result = mvc.perform(request(PATH, OWNER).param("asOf", DAY.toString()))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("secret", "SQL", "owner", "stackTrace");
	}
	private ObjectNode notFound(String path) throws Exception {
		var result = mvc.perform(request(path, OTHER).param("asOf", DAY.toString()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("Private", OWNER.toString(), "totalTasks");
		return (ObjectNode) mapper.readTree(result.getResponse().getContentAsString());
	}
	private MockHttpServletRequestBuilder request(String path, UUID owner) {
		return get(path).header("Authorization", "Bearer " + tokens.issue(owner).value());
	}
	private void owned() {
		when(boards.findByIdAndOwnerId(ID, OWNER)).thenReturn(Optional.of(new BoardEntity(OWNER, "Private")));
		when(tasks.aggregateBoardTotals(ID, OWNER, DAY)).thenReturn(mock(TaskRepository.Totals.class));
	}
	@TestConfiguration(proxyBeanMethods = false)
	@EnableTransactionManagement
	static class Transactions {}
}
