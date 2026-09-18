package com.taskflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.task.domain.TaskTitle;
import com.taskflow.task.domain.TaskDescription;
import com.taskflow.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateTaskRequest(@NotBlank @Size(max = TaskTitle.MAX_LENGTH) @Schema(example = "Review release notes") String title,
		@Size(max = TaskDescription.MAX_LENGTH) @Schema(description = "Optional plain text; blank normalizes to null.", nullable = true, example = "Check the migration instructions before release.") String description,
		@NotNull @Schema(example = "MEDIUM") TaskPriority priority,
		@Schema(description = "Optional civil date without a time or time zone.", nullable = true, type = "string", format = "date", example = "2026-09-20") LocalDate dueDate) {
	public UpdateTaskRequest {
		title = TaskTitle.normalize(title);
		description = TaskDescription.normalize(description);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only task content may be supplied.");
	}
}
