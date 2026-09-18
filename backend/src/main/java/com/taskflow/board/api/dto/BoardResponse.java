package com.taskflow.board.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import com.taskflow.board.application.Board;

public record BoardResponse(@Schema(example = "8bf24ae0-bd7e-4c3e-b226-38ce5db44b35") UUID id,
		@Schema(example = "Release planning") String name,
		Instant createdAt,
		Instant updatedAt) {

	public static BoardResponse from(Board board) {
		return new BoardResponse(board.id(), board.name(), board.createdAt(), board.updatedAt());
	}
}
