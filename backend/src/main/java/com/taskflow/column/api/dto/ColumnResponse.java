package com.taskflow.column.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import com.taskflow.column.application.Column;

public record ColumnResponse(@Schema(example = "8bf24ae0-bd7e-4c3e-b226-38ce5db44b35") UUID id,
		@Schema(example = "In progress") String name,
		@Schema(description = "Zero-based canonical position.", example = "0") int position,
		Instant createdAt,
		Instant updatedAt) {

	public static ColumnResponse from(Column column) {
		return new ColumnResponse(column.id(), column.name(), column.position(), column.createdAt(), column.updatedAt());
	}
}
