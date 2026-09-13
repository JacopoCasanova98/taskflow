package com.taskflow.column.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.column.domain.ColumnName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(@NotBlank @Size(max = ColumnName.MAX_LENGTH) String name) {

	public CreateColumnRequest {
		name = ColumnName.normalize(name);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only the column name may be supplied.");
	}
}
