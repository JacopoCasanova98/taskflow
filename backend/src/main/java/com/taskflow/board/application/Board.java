package com.taskflow.board.application;

import java.time.Instant;
import java.util.UUID;

public record Board(UUID id, String name, Instant createdAt, Instant updatedAt) {
}
