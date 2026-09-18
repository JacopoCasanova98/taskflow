package com.taskflow.shared.security;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityAuthenticatedUserProvider implements AuthenticatedUserProvider {
	@Override
	public AuthenticatedUser currentUser() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
			String subject = token.getToken().getSubject();
			try {
				if (subject != null) {
					UUID id = UUID.fromString(subject);
					if (id.toString().equalsIgnoreCase(subject)) { return new AuthenticatedUser(id); }
				}
			} catch (IllegalArgumentException ignored) {
				// Decoder validation prevents this for real requests; never expose parser inputs or causes.
			}
		}
		throw new AuthenticationCredentialsNotFoundException("Authenticated identity is unavailable.");
	}
}
