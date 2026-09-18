package com.taskflow.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AuthenticationResponse(CurrentUserResponse user,
		@Schema(description = "Short-lived Bearer access token; refresh token is only in the HttpOnly cookie.") String accessToken,
		Instant accessTokenExpiresAt) {
	@Override
	public String toString() { return "AuthenticationResponse[credentials=REDACTED]"; }
}
