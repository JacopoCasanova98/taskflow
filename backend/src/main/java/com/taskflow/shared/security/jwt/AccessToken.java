package com.taskflow.shared.security.jwt;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {

	@Override
	public String toString() {
		return "AccessToken[value=REDACTED, expiresAt=" + expiresAt + "]";
	}
}
