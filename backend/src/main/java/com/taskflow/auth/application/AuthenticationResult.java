package com.taskflow.auth.application;

import java.util.UUID;
import com.taskflow.shared.security.jwt.AccessToken;

public record AuthenticationResult(UUID userId, String email, AccessToken accessToken, String refreshToken) {
	@Override
	public String toString() { return "AuthenticationResult[credentials=REDACTED]"; }
}
