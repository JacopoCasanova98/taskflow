package com.taskflow.shared.security.jwt;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {
	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		String subject = jwt.getSubject();
		if (subject != null && !subject.isBlank()) {
			try {
				// UUID.fromString also accepts shortened groups; require the full UUID representation.
				if (UUID.fromString(subject).toString().equalsIgnoreCase(subject)) {
					return OAuth2TokenValidatorResult.success();
				}
			} catch (IllegalArgumentException ignored) {
				// The public authentication failure must not contain the subject or parser details.
			}
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid subject.", null));
	}
}
