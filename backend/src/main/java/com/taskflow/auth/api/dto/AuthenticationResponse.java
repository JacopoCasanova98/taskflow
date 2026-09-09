package com.taskflow.auth.api.dto;

import java.time.Instant;

public record AuthenticationResponse(CurrentUserResponse user, String accessToken, Instant accessTokenExpiresAt) {
	@Override
	public String toString() { return "AuthenticationResponse[credentials=REDACTED]"; }
}
