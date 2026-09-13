package com.taskflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.task.application.Task;

public record TaskResponse(@Schema(example = "8bf24ae0-bd7e-4c3e-b226-38ce5db44b35") UUID id,
		@Schema(description = "Column UUID; placement targets must belong to the same Board.", example = "f41f5c06-5278-41e7-862a-0ee44104be5c") UUID columnId,
		@Schema(example = "Review release notes") String title,
		@Schema(description = "Optional plain text; blank normalizes to null.", nullable = true, example = "Check the migration instructions before release.") String description,
		@Schema(example = "MEDIUM") TaskPriority priority,
		@Schema(description = "Optional civil date without a time or time zone.", nullable = true, type = "string", format = "date", example = "2026-09-20") LocalDate dueDate,
		@Schema(description = "Zero-based canonical position.", example = "0") int position,
		Instant createdAt,
		Instant updatedAt) {
	public static TaskResponse from(Task task) {
		return new TaskResponse(task.id(), task.columnId(), task.title(), task.description(), task.priority(),
				task.dueDate(), task.position(), task.createdAt(), task.updatedAt());
	}
}
