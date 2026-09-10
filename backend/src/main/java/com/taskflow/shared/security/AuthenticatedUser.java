package com.taskflow.shared.security;

import java.util.Objects;
import java.util.UUID;

/** Durable application identity, independent of authentication credentials and profile data. */
public record AuthenticatedUser(UUID id) {
	public AuthenticatedUser { Objects.requireNonNull(id, "Authenticated user id is required."); }
}
