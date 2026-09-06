package com.taskflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

	private final PasswordEncoder encoder = new SecurityConfiguration().passwordEncoder();

	@Test
	void encodesWithArgon2idAndMatchesOnlyCorrectPassword() {
		String password = "a sufficiently long password";
		String encoded = encoder.encode(password);

		assertThat(encoded).isNotEqualTo(password).startsWith("{argon2id}$argon2id$");
		assertThat(encoded.length()).isLessThanOrEqualTo(255);
		assertThat(encoder.matches(password, encoded)).isTrue();
		assertThat(encoder.matches("a different password", encoded)).isFalse();
	}

	@Test
	void generatesDifferentSaltForEachEncoding() {
		String password = "a sufficiently long password";
		assertThat(encoder.encode(password)).isNotEqualTo(encoder.encode(password));
	}

	@Test
	void supportsFull128CharacterPasswordWithoutTruncation() {
		String password = "é".repeat(128);
		String encoded = encoder.encode(password);

		assertThat(encoded.length()).isLessThanOrEqualTo(255);
		assertThat(encoder.matches(password, encoded)).isTrue();
		assertThat(encoder.matches("é".repeat(127) + "x", encoded)).isFalse();
	}

	@Test
	void rejectsUnknownMissingAndNoopEncodingIds() {
		for (String encoded : new String[] {"{unknown}hash", "unprefixed", "{noop}plaintext"}) {
			assertThatIllegalArgumentException().isThrownBy(() -> encoder.matches("plaintext", encoded));
		}
	}
}
