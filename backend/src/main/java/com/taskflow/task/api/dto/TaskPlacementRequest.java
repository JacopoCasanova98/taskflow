package com.taskflow.task.api.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TaskPlacementRequest(@NotNull UUID columnId, @NotNull @Min(0) Integer position) {
	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only columnId and position may be supplied.");
	}
}
