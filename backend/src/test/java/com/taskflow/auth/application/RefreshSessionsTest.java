package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshSessionsTest {
	private final Instant now = Instant.parse("2026-09-06T12:00:00Z");
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
	private final RefreshSessions sessions = new RefreshSessions(repository, new RefreshSessionProperties(Duration.ofDays(30)),
			Clock.fixed(now, ZoneOffset.UTC));

	@Test
	void generatesIndependent256BitTokensAndPersistsOnlyHashesWithExactExpiry() {
		UUID userId = UUID.randomUUID();
		String first = sessions.issue(userId, null);
		String second = sessions.issue(userId, null);
		assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
		assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
		var captured = ArgumentCaptor.forClass(RefreshTokenEntity.class);
		verify(repository, times(2)).saveAndFlush(captured.capture());
		var entity = captured.getAllValues().getFirst();
		assertThat(entity.getTokenHash()).isEqualTo(RefreshSessions.hash(first)).matches("[a-f0-9]{64}").isNotEqualTo(first);
		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
		assertThat(entity.getRevokedAt()).isNull();
	}

	@Test
	void hashesDeterministicallyUsingSha256() {
		assertThat(RefreshSessions.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void replacesKnownBrowserSessionAndDoesNotBlockUnknownCookies() {
		var old = new RefreshTokenEntity(UUID.randomUUID(), RefreshSessions.hash("old-cookie"), now.plusSeconds(600));
		when(repository.findByTokenHash(RefreshSessions.hash("old-cookie"))).thenReturn(Optional.of(old));
		sessions.issue(UUID.randomUUID(), "old-cookie");
		assertThat(old.getRevokedAt()).isEqualTo(now);
		assertThat(sessions.issue(UUID.randomUUID(), "unrecognized")).isNotBlank();
		verify(repository, times(2)).saveAndFlush(any());
	}
}
