package com.taskflow.shared.logging;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

class MutationLogTest {
	@Test
	void successIsEmittedOnlyAfterCommit() {
		try (var logs = new LogCapture(MutationLog.class)) {
			new TransactionTemplate(manager(false)).executeWithoutResult(status -> {
				MutationLog.afterCommit(LoggerFactory.getLogger(MutationLog.class), "event=board_created boardId={}", java.util.UUID.randomUUID());
				assertThat(logs.events()).isEmpty();
			});
			assertThat(logs.events()).hasSize(1);
		}
	}

	@Test
	void rollbackDoesNotEmitSuccess() {
		try (var logs = new LogCapture(MutationLog.class)) {
			new TransactionTemplate(manager(false)).executeWithoutResult(status -> {
				MutationLog.afterCommit(LoggerFactory.getLogger(MutationLog.class), "event=board_deleted");
				status.setRollbackOnly();
			});
			assertThat(logs.events()).isEmpty();
		}
	}

	@Test
	void commitFailureDoesNotEmitSuccess() {
		try (var logs = new LogCapture(MutationLog.class)) {
			assertThatThrownBy(() -> new TransactionTemplate(manager(true)).executeWithoutResult(status ->
					MutationLog.afterCommit(LoggerFactory.getLogger(MutationLog.class), "event=columns_reordered")))
					.isInstanceOf(IllegalStateException.class);
			assertThat(logs.events()).isEmpty();
		}
	}

	private AbstractPlatformTransactionManager manager(boolean fail) {
		return new AbstractPlatformTransactionManager() {
			protected Object doGetTransaction() { return new Object(); }
			protected void doBegin(Object transaction, TransactionDefinition definition) {}
			protected void doCommit(DefaultTransactionStatus status) {
				if (fail) throw new IllegalStateException("deterministic commit failure");
			}
			protected void doRollback(DefaultTransactionStatus status) {}
		};
	}
}
