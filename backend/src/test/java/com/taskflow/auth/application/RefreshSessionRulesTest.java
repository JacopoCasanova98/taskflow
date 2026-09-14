package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshSessionRulesTest {
	private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
	private static final UUID USER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
	private static final UUID FAMILY = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
	private static final String PRESENTED = "a".repeat(43);
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
	private final RefreshSessions tokens = mock(RefreshSessions.class);
	private final UserRepository users = mock(UserRepository.class);
	private final AccessTokenService access = mock(AccessTokenService.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final RefreshSessionLifecycle lifecycle = new RefreshSessionLifecycle(repository, tokens, users, access,
			Clock.fixed(NOW, ZoneOffset.UTC), transactions);

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "short", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!"})
	void malformedSessionNeverStartsRefreshOrLogoutWork(String raw) {
		assertThat(lifecycle.refresh(raw)).isEmpty();
		lifecycle.logout(raw);

		verifyNoInteractions(repository, tokens, users, access, transactions);
	}

	@Test
	void unknownWellFormedSessionReturnsEmptyWithoutIssuingCredentials() {
		transaction();

		assertThat(lifecycle.refresh(PRESENTED)).isEmpty();

		verify(tokens).findForConsumption(PRESENTED);
		verify(tokens, never()).rotate(any(), any());
		verifyNoInteractions(users, access, repository);
	}

	@Test
	void deletedUserPreventsRotationWithoutRevokingExistingToken() {
		transaction();
		var token = new RefreshTokenEntity(USER, "stored-hash", NOW.plusSeconds(60), FAMILY);
		when(tokens.findForConsumption(PRESENTED)).thenReturn(Optional.of(token));

		assertThat(lifecycle.refresh(PRESENTED)).isEmpty();

		verify(users).findById(USER);
		verify(tokens, never()).rotate(any(), any());
		verifyNoInteractions(access, repository);
		assertThat(token.isRevoked()).isFalse();
	}

	@Test
	void repeatedLogoutRetainsFirstRevocationTimeWithoutIssuingCredentials() {
		transaction();
		var token = new RefreshTokenEntity(USER, "stored-hash", NOW.plusSeconds(60), FAMILY);
		when(tokens.findForConsumption(PRESENTED)).thenReturn(Optional.of(token));

		lifecycle.logout(PRESENTED);
		var later = new RefreshSessionLifecycle(repository, tokens, users, access,
				Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), transactions);
		later.logout(PRESENTED);

		assertThat(token.getRevokedAt()).isEqualTo(NOW);
		verifyNoInteractions(users, access, repository);
		verify(tokens, never()).rotate(any(), any());
	}

	private void transaction() {
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}
}
