package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshSessionPropertiesTest {
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"PT0S", "PT-1S", "PT0.5S", "PT1.000000001S"})
	void rejectsAbsentNonPositiveOrFractionalLifetime(String value) {
		Duration lifetime = value == null ? null : Duration.parse(value);
		assertThatIllegalArgumentException().isThrownBy(() -> new RefreshSessionProperties(lifetime));
	}

	@Test
	void acceptsMinimumWholeSecondAndLongerWholeSecondLifetimes() {
		assertThatCode(() -> new RefreshSessionProperties(Duration.ofSeconds(1))).doesNotThrowAnyException();
		assertThatCode(() -> new RefreshSessionProperties(Duration.ofDays(30))).doesNotThrowAnyException();
	}
}
