package com.taskflow.board.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.board.domain.BoardName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RenameBoardRequest(@NotBlank @Size(max = BoardName.MAX_LENGTH) @Schema(example = "Release planning") String name) {

	public RenameBoardRequest {
		name = BoardName.normalize(name);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only the board name may be supplied.");
	}
}
