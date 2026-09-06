package com.taskflow.shared.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;

import com.taskflow.shared.config.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(JwtConfiguration.class, TimeConfiguration.class);

	@Test
	void bindsDefaultsAndProvidesOneUtcClockWithoutPersistenceAuditing() {
		runner.withPropertyValues("taskflow.security.jwt.secret-base64=" + Base64.getEncoder().encodeToString(new byte[32]))
				.run(context -> {
					assertThat(context).hasNotFailed().hasSingleBean(Clock.class);
					assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
					JwtProperties properties = context.getBean(JwtProperties.class);
					assertThat(properties.issuer()).isEqualTo("taskflow");
					assertThat(properties.audience()).isEqualTo("taskflow-api");
					assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
					assertThat(properties.toString()).doesNotContain(properties.secretBase64());
				});
	}

	@Test
	void failsStartupWithoutSecret() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsStartupForBlankMalformedAndWrongLengthSecretsWithoutEchoingValues() {
		for (String secret : new String[] {" ", "sensitive-invalid-base64!",
				Base64.getEncoder().encodeToString(new byte[31]), Base64.getEncoder().encodeToString(new byte[33])}) {
			runner.withPropertyValues("taskflow.security.jwt.secret-base64=" + secret).run(context -> {
				assertThat(context).hasFailed();
				Throwable failure = context.getStartupFailure();
				while (failure.getCause() != null) {
					if (!secret.isBlank()) {
						assertThat(failure.getMessage()).doesNotContain(secret);
					}
					failure = failure.getCause();
				}
				assertThat(failure).isInstanceOf(IllegalStateException.class);
				if (!secret.isBlank()) {
					assertThat(failure.getMessage()).doesNotContain(secret);
				}
			});
		}
	}
}
