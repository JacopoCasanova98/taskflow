package com.taskflow.task.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.taskflow.task.domain.TaskPriority;

public record Task(UUID id, UUID columnId, String title, String description, TaskPriority priority,
		LocalDate dueDate, int position, Instant createdAt, Instant updatedAt) {}
