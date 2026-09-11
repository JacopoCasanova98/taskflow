package com.taskflow.column.api.dto;

import java.time.Instant;
import java.util.UUID;
import com.taskflow.column.application.Column;

public record ColumnResponse(UUID id, String name, int position, Instant createdAt, Instant updatedAt) {

	public static ColumnResponse from(Column column) {
		return new ColumnResponse(column.id(), column.name(), column.position(), column.createdAt(), column.updatedAt());
	}
}
