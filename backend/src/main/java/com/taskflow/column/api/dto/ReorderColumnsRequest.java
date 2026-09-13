package com.taskflow.column.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ReorderColumnsRequest(@NotNull @Schema(description = "Every current Board Column ID exactly once, in desired order.") List<@NotNull UUID> columnIds) {

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only columnIds may be supplied.");
	}
}
