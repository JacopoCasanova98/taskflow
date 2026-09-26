package com.taskflow.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(
		name = "taskflow.persistence.auditing.enabled",
		havingValue = "true",
		matchIfMissing = true
)
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class PersistenceConfiguration {

	@Bean
	DateTimeProvider utcDateTimeProvider(Clock utcClock) {
		// PostgreSQL persists microseconds; returned entities must match later reloads.
		return () -> Optional.of(Instant.now(utcClock).truncatedTo(ChronoUnit.MICROS));
	}
}
