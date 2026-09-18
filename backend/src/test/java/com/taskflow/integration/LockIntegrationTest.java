package com.taskflow.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LockIntegrationTest extends PostgresIntegrationSupport {
	@Test
	void boardWriteQueryBlocksAnotherTransactionUntilCommit() throws Exception {
		var owner = user("lock@example.com");
		actAs(owner.getId());
		var board = boardService.createBoard("Board");
		assertExclusive(() -> boards.findByIdAndOwnerIdForUpdate(board.id(), owner.getId()).orElseThrow());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void refreshFamilyAndTokenQueriesAcquireExclusiveLocks(boolean familyQuery) throws Exception {
		var owner = user("refresh-lock@example.com");
		var root = sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), "e".repeat(64), Instant.parse("2030-01-01T00:00:00Z")));
		var child = sessions.saveAndFlush(new RefreshTokenEntity(owner.getId(), "f".repeat(64),
				Instant.parse("2030-01-01T00:00:00Z"), root.getFamilyId()));
		transaction().executeWithoutResult(status -> sessions.findById(root.getId()).orElseThrow()
				.markRotated(child.getId(), Instant.parse("2026-09-14T00:00:00Z")));
		if (familyQuery) {
			assertExclusive(() -> assertThat(sessions.lockFamilyRoot(root.getFamilyId()).orElseThrow().getId()).isEqualTo(root.getId()));
		} else {
			assertExclusive(() -> sessions.findByTokenHashForUpdate(child.getTokenHash()).orElseThrow());
		}
	}

	private void assertExclusive(Runnable acquire) throws Exception {
		var executor = Executors.newSingleThreadExecutor();
		var contenderPid = new CompletableFuture<Integer>();
		var contender = new CompletableFuture<Void>();
		try {
			transaction().executeWithoutResult(status -> {
				jdbc.execute("SET LOCAL statement_timeout = '8s'");
				acquire.run();
				executor.submit(() -> {
					try {
						transaction().executeWithoutResult(other -> {
							jdbc.execute("SET LOCAL statement_timeout = '8s'");
							contenderPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
							acquire.run();
						});
						contender.complete(null);
					} catch (Throwable failure) {
						contenderPid.completeExceptionally(failure);
						contender.completeExceptionally(failure);
					}
				});
				try {
					int pid = contenderPid.get(5, TimeUnit.SECONDS);
					long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
					boolean blocked = false;
					while (!blocked && !contender.isDone() && System.nanoTime() < deadline) {
						blocked = Boolean.TRUE.equals(jdbc.queryForObject(
								"SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid));
					}
					// Observe an actual PostgreSQL blocker, rather than assuming a slow thread is waiting on a lock.
					assertThat(blocked).isTrue();
					assertThat(contender).isNotDone();
				} catch (Exception failure) {
					throw new AssertionError("Contender failed before the lock could be observed", failure);
				}
			});
			contender.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}
}
