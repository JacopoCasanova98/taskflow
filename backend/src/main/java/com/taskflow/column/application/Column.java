package com.taskflow.column.application;

import java.time.Instant;
import java.util.UUID;

public record Column(UUID id, String name, int position, Instant createdAt, Instant updatedAt) {
}
