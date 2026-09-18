package com.taskflow.auth.persistence;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure session state transitions; no JPA mapping or database execution. */
class RefreshTokenEntityTest {
	private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
	private static final UUID USER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
	private static final UUID FAMILY = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
	private static final UUID REPLACEMENT = UUID.fromString("750e8400-e29b-41d4-a716-446655440000");

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 1})
	void rotationRespectsExactExpirationBoundary(long remainingNanos) {
		var token = new RefreshTokenEntity(USER, "stored-hash", NOW.plusNanos(remainingNanos), FAMILY);

		if (remainingNanos > 0) {
			assertThat(token.isExpired(NOW)).isFalse();
			token.markRotated(REPLACEMENT, NOW);
			assertThat(token.getReplacedByTokenId()).isEqualTo(REPLACEMENT);
			assertThat(token.getRevokedAt()).isEqualTo(NOW);
			assertThat(token.getFamilyId()).isEqualTo(FAMILY);
		} else {
			assertThat(token.isExpired(NOW)).isTrue();
			assertThatIllegalStateException().isThrownBy(() -> token.markRotated(REPLACEMENT, NOW));
			assertThat(token.getReplacedByTokenId()).isNull();
			assertThat(token.isRevoked()).isFalse();
		}
	}

	@Test
	void revokedSessionCannotBeRotatedAgainOrLoseItsHistory() {
		var token = new RefreshTokenEntity(USER, "stored-hash", NOW.plusSeconds(60), FAMILY);
		token.markRotated(REPLACEMENT, NOW.minusSeconds(1));

		assertThatIllegalStateException().isThrownBy(() -> token.markRotated(USER, NOW));

		assertThat(token.getReplacedByTokenId()).isEqualTo(REPLACEMENT);
		assertThat(token.getRevokedAt()).isEqualTo(NOW.minusSeconds(1));
	}

	@Test
	void missingReplacementCannotPartiallyRevokeAnActiveSession() {
		var token = new RefreshTokenEntity(USER, "stored-hash", NOW.plusSeconds(60), FAMILY);

		assertThatNullPointerException().isThrownBy(() -> token.markRotated(null, NOW));

		assertThat(token.isRevoked()).isFalse();
		assertThat(token.getReplacedByTokenId()).isNull();
	}
}
