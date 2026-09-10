package com.taskflow.board.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.board.domain.BoardName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(@NotBlank @Size(max = BoardName.MAX_LENGTH) String name) {

	public CreateBoardRequest {
		name = BoardName.normalize(name);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object value) {
		throw new IllegalArgumentException("Only the board name may be supplied.");
	}
}
