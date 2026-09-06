package com.taskflow.user.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserEntityTest {

	@Test
	void constructionNormalizesEmailAndPreservesSuppliedPasswordHash() {
		String passwordHash = "encoded-hash-fixture";

		UserEntity user = new UserEntity("  USER@EXAMPLE.COM\t", passwordHash);

		assertThat(user.getEmail()).isEqualTo("user@example.com");
		assertThat(user.getPasswordHash()).isEqualTo(passwordHash);
	}
}
