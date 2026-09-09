package com.taskflow.shared.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtFoundationTest {

	private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("c0258a80-f7f2-4f31-a792-a91aeb786eac");
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final JwtProperties properties = properties(new byte[32]);
	private final JwtConfiguration configuration = new JwtConfiguration();
	private final JwtEncoder encoder = configuration.jwtEncoder(configuration.jwtSecretKey(properties));
	private final JwtDecoder decoder = configuration.jwtDecoder(configuration.jwtSecretKey(properties), properties, clock);
	private final AccessTokenService service = new AccessTokenService(encoder, properties, clock);

	@Test
	void issuesOnlyRequiredClaimsWithHs256AndExactExpiry() {
		AccessToken token = service.issue(USER_ID);
		Jwt jwt = decoder.decode(token.value());

		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti");
		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("taskflow");
		assertThat(jwt.getAudience()).containsExactly("taskflow-api");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900)).isEqualTo(token.expiresAt());
		assertThat(UUID.fromString(jwt.getId()).toString()).isEqualTo(jwt.getId());
		assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
		assertThat(decoder.decode(service.issue(USER_ID).value()).getId()).isNotEqualTo(jwt.getId());
		assertThat(token.toString()).doesNotContain(token.value());
	}

	@Test
	void rejectsExpiredTokens() {
		assertRejected(sign(claims().issuedAt(NOW.minusSeconds(1800)).expiresAt(NOW.minusSeconds(900))));
	}

	@Test
	void rejectsDifferentSigningSecret() {
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;
		JwtEncoder otherEncoder = configuration.jwtEncoder(configuration.jwtSecretKey(properties(otherKey)));
		assertRejected(new AccessTokenService(otherEncoder, properties, clock).issue(USER_ID).value());
	}

	@Test
	void rejectsWrongIssuer() {
		assertRejected(sign(claims().issuer("other-issuer")));
	}

	@Test
	void rejectsMissingAndWrongAudience() {
		assertRejected(sign(claims().claims(values -> values.remove("aud"))));
		assertRejected(sign(claims().audience(List.of("other-api"))));
	}

	@Test
	void rejectsTamperedSignature() {
		String token = service.issue(USER_ID).value();
		int signatureStart = token.lastIndexOf('.') + 1;
		char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		assertRejected(token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1));
	}

	@Test
	void rejectsMissingExpiration() {
		assertRejected(sign(claims().claims(values -> values.remove("exp"))));
	}

	@Test
	void rejectsFutureNotBefore() {
		assertRejected(sign(claims().notBefore(NOW.plusSeconds(300))));
	}

	@Test
	void rejectsMissingBlankMalformedAndShortenedUuidSubjects() {
		assertRejected(sign(claims().claims(values -> values.remove("sub"))));
		for (String subject : new String[] {"", " ", "private-invalid-subject", "1-1-1-1-1"}) {
			assertRejected(sign(claims().subject(subject)));
		}
	}

	private JwtClaimsSet.Builder claims() {
		return JwtClaimsSet.builder().issuer("taskflow").subject(USER_ID.toString())
				.audience(List.of("taskflow-api")).issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
				.id(UUID.randomUUID().toString());
	}

	private String sign(JwtClaimsSet.Builder claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
				claims.build())).getTokenValue();
	}

	private void assertRejected(String token) {
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private static JwtProperties properties(byte[] key) {
		return new JwtProperties(Base64.getEncoder().encodeToString(key), "taskflow", "taskflow-api", Duration.ofMinutes(15));
	}
}
