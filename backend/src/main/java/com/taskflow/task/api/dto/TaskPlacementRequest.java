package com.taskflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record TaskPlacementRequest(@NotNull @Schema(description = "Column UUID; placement targets must belong to the same Board.", example = "f41f5c06-5278-41e7-862a-0ee44104be5c") UUID columnId,
		@NotNull @Min(0) @Schema(description = "Zero-based canonical position.", example = "0") Integer position) {
	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only columnId and position may be supplied.");
	}
}
