package com.taskflow.board.api.dto;

import java.time.Instant;
import java.util.UUID;
import com.taskflow.board.application.Board;

public record BoardResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

	public static BoardResponse from(Board board) {
		return new BoardResponse(board.id(), board.name(), board.createdAt(), board.updatedAt());
	}
}
