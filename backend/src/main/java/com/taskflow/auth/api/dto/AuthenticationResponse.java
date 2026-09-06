package com.taskflow.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationResponse(CurrentUser user, String accessToken, Instant accessTokenExpiresAt) {
	public record CurrentUser(UUID id, String email) { }
	@Override
	public String toString() { return "AuthenticationResponse[credentials=REDACTED]"; }
}
