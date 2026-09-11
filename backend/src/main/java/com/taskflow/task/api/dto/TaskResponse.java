package com.taskflow.task.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.task.application.Task;

public record TaskResponse(UUID id, UUID columnId, String title, String description, TaskPriority priority,
		LocalDate dueDate, int position, Instant createdAt, Instant updatedAt) {
	public static TaskResponse from(Task task) {
		return new TaskResponse(task.id(), task.columnId(), task.title(), task.description(), task.priority(),
				task.dueDate(), task.position(), task.createdAt(), task.updatedAt());
	}
}
