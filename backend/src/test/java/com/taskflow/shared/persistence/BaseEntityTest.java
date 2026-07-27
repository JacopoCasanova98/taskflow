package com.taskflow.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

class BaseEntityTest {

	@Test
	void definesSharedUuidAndAuditMapping() throws NoSuchFieldException {
		assertThat(BaseEntity.class.isAnnotationPresent(MappedSuperclass.class)).isTrue();

		Field id = BaseEntity.class.getDeclaredField("id");
		assertThat(id.getType()).isEqualTo(UUID.class);
		assertThat(id.isAnnotationPresent(Id.class)).isTrue();
		assertThat(id.getAnnotation(UuidGenerator.class).style()).isEqualTo(UuidGenerator.Style.RANDOM);
		assertThat(id.getAnnotation(Column.class).updatable()).isFalse();

		Field createdAt = BaseEntity.class.getDeclaredField("createdAt");
		assertThat(createdAt.getType()).isEqualTo(Instant.class);
		assertThat(createdAt.isAnnotationPresent(CreatedDate.class)).isTrue();
		assertThat(createdAt.getAnnotation(Column.class).updatable()).isFalse();

		Field updatedAt = BaseEntity.class.getDeclaredField("updatedAt");
		assertThat(updatedAt.getType()).isEqualTo(Instant.class);
		assertThat(updatedAt.isAnnotationPresent(LastModifiedDate.class)).isTrue();
	}

	@Test
	void providesAuditTimestampsFromUtcClock() {
		PersistenceConfiguration configuration = new PersistenceConfiguration();
		Clock clock = configuration.utcClock();
		Optional<TemporalAccessor> currentTime = configuration.utcDateTimeProvider(clock).getNow();

		assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
		assertThat(currentTime).hasValueSatisfying(value -> assertThat(value).isInstanceOf(Instant.class));
	}
}
