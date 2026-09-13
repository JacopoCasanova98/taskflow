package com.taskflow.column.api.dto;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

public record ReorderColumnsRequest(@NotNull List<@NotNull UUID> columnIds) {

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only columnIds may be supplied.");
	}
}
