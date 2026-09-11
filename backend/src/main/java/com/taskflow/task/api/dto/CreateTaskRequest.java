package com.taskflow.task.api.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.task.domain.TaskTitle;
import com.taskflow.task.domain.TaskDescription;
import com.taskflow.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(@NotBlank @Size(max = TaskTitle.MAX_LENGTH) String title,
		@Size(max = TaskDescription.MAX_LENGTH) String description,
		TaskPriority priority, LocalDate dueDate) {
	public CreateTaskRequest {
		title = TaskTitle.normalize(title);
		description = TaskDescription.normalize(description);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only task content may be supplied.");
	}
}
