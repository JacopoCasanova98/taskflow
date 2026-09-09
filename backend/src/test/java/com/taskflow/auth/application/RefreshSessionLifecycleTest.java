package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import com.taskflow.auth.persistence.*;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.user.persistence.*;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshSessionLifecycleTest {
	private final Instant now = Instant.parse("2026-09-09T12:00:00Z");
	private final UUID userId = UUID.randomUUID();
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final AccessTokenService access = mock(AccessTokenService.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final RefreshSessions tokens = new RefreshSessions(repository,
			new RefreshSessionProperties(Duration.ofDays(7)), Clock.fixed(now, ZoneOffset.UTC));
	private final RefreshSessionLifecycle lifecycle = new RefreshSessionLifecycle(repository, tokens, users,
			access, Clock.fixed(now, ZoneOffset.UTC), transactions);
	private final Map<String, RefreshTokenEntity> rows = new HashMap<>();

	@BeforeEach
	void persistence() {
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(repository.findFamilyIdByTokenHash(any())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))).map(RefreshTokenEntity::getFamilyId));
		when(repository.lockFamilyRoot(any())).thenAnswer(call -> rows.values().stream().filter(token -> token.getFamilyId().equals(call.getArgument(0))).findFirst());
		when(repository.findByTokenHashForUpdate(any())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
		when(repository.saveAndFlush(any())).thenAnswer(call -> {
			RefreshTokenEntity token = call.getArgument(0);
			ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
			rows.put(token.getTokenHash(), token);
			return token;
		});
		when(repository.revokeActiveByFamilyId(any(), any())).thenAnswer(call -> {
			UUID family = call.getArgument(0);
			rows.values().stream().filter(token -> token.getFamilyId().equals(family)).forEach(token -> token.revoke(call.getArgument(1)));
			return 1;
		});
		UserEntity user = new UserEntity("current@example.com", "unused");
		ReflectionTestUtils.setField(user, "id", userId);
		when(users.findById(userId)).thenReturn(Optional.of(user));
	}

	@Test
	void rotationPreservesHistoryAndFreshConfiguredTtlAndIssuesAccessForUuid() {
		String raw = tokens.issue(userId, null);
		var old = rows.get(RefreshSessions.hash(raw));
		var result = lifecycle.refresh(raw).orElseThrow();
		var child = rows.get(RefreshSessions.hash(result.refreshToken()));
		assertThat(result.refreshToken()).isNotEqualTo(raw).matches("[A-Za-z0-9_-]{43}");
		assertThat(result.userId()).isEqualTo(userId);
		assertThat(result.email()).isEqualTo("current@example.com");
		assertThat(old.getTokenHash()).isEqualTo(RefreshSessions.hash(raw));
		assertThat(old.getRevokedAt()).isEqualTo(now);
		assertThat(old.getReplacedByTokenId()).isEqualTo(child.getId());
		assertThat(child.getFamilyId()).isEqualTo(old.getFamilyId());
		assertThat(child.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
		assertThat(child.isRevoked()).isFalse();
		assertThat(rows).hasSize(2);
		verify(access).issue(userId);
		verify(transactions).commit(any());
	}

	@Test
	void replayOfEitherAncestorRevokesDescendantButPreservesIndependentFamily() {
		for (boolean replayRoot : new boolean[] {true, false}) {
			String a = tokens.issue(userId, null);
			String independent = tokens.issue(userId, null);
			String b = lifecycle.refresh(a).orElseThrow().refreshToken();
			String c = lifecycle.refresh(b).orElseThrow().refreshToken();
			clearInvocations(transactions);
			assertThat(lifecycle.refresh(replayRoot ? a : b)).isEmpty();
			assertThat(rows.get(RefreshSessions.hash(c)).isRevoked()).isTrue();
			assertThat(rows.get(RefreshSessions.hash(independent)).isRevoked()).isFalse();
			verify(transactions).commit(any());
			verify(transactions, never()).rollback(any());
			assertThat(lifecycle.refresh(c)).isEmpty();
			assertThat(lifecycle.refresh(independent)).isPresent();
		}
	}

	@Test
	void expiredAtExactBoundaryAndRevokedTokensDoNotTriggerReplay() {
		String raw = "e".repeat(43);
		var expired = new RefreshTokenEntity(userId, RefreshSessions.hash(raw), now);
		rows.put(expired.getTokenHash(), expired);
		assertThat(lifecycle.refresh(raw)).isEmpty();
		var revoked = new RefreshTokenEntity(userId, expired.getTokenHash(), now.plusSeconds(60));
		revoked.revoke(now.minusSeconds(10));
		rows.put(revoked.getTokenHash(), revoked);
		assertThat(lifecycle.refresh(raw)).isEmpty();
		lifecycle.logout(raw);
		assertThat(revoked.getRevokedAt()).isEqualTo(now.minusSeconds(10));
		verify(repository, never()).revokeActiveByFamilyId(any(), any());
		verifyNoInteractions(access);
	}

	@Test
	void replacementFailureRollsBackWithoutReturningCredentials() {
		String raw = tokens.issue(userId, null);
		doThrow(new DataAccessResourceFailureException("failure")).when(repository).saveAndFlush(any());
		assertThatThrownBy(() -> lifecycle.refresh(raw)).isInstanceOf(DataAccessResourceFailureException.class);
		verify(transactions).rollback(any());
		verify(transactions, never()).commit(any());
		verifyNoInteractions(access);
	}

	@Test
	void commitFailureCannotReturnCredentials() {
		String raw = tokens.issue(userId, null);
		doThrow(new DataAccessResourceFailureException("commit failed")).when(transactions).commit(any());
		assertThatThrownBy(() -> lifecycle.refresh(raw)).isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	void familyRootIsLockedBeforeCurrentTokenAndMissingFamilyStopsConsumption() {
		String raw = tokens.issue(userId, null);
		var token = rows.get(RefreshSessions.hash(raw));
		clearInvocations(repository);
		assertThat(lifecycle.refresh(raw)).isPresent();
		var order = inOrder(repository);
		order.verify(repository).findFamilyIdByTokenHash(RefreshSessions.hash(raw));
		order.verify(repository).lockFamilyRoot(token.getFamilyId());
		order.verify(repository).findByTokenHashForUpdate(RefreshSessions.hash(raw));
		order.verify(repository).saveAndFlush(any());
		clearInvocations(repository);
		doReturn(Optional.empty()).when(repository).lockFamilyRoot(any());
		assertThat(lifecycle.refresh(raw)).isEmpty();
		verify(repository, never()).findByTokenHashForUpdate(any());
	}

	@Test
	void consumptionDeclaresDatabaseWriteLock() throws Exception {
		var lookup = RefreshTokenRepository.class.getMethod("findByTokenHashForUpdate", String.class);
		assertThat(lookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(RefreshTokenRepository.class.getMethod("lockFamilyRoot", UUID.class).getAnnotation(Lock.class).value())
				.isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		// Metadata only: mocks do not prove PostgreSQL row locking.
	}
}
