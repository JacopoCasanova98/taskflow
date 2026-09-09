package com.taskflow.shared.security;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SpringSecurityAuthenticatedUserProviderTest {
	private final AuthenticatedUserProvider provider = new SpringSecurityAuthenticatedUserProvider();

	@BeforeEach
	void cleanContext() { SecurityContextHolder.clearContext(); }
	@AfterEach
	void clearContext() { SecurityContextHolder.clearContext(); }

	@Test
	void resolvesUuidWithoutProfileClaimsOrDatabaseDependencies() {
		UUID id = UUID.randomUUID();
		for (String email : new String[] {"first@example.com", "changed@example.com", ""}) {
			var builder = Jwt.withTokenValue("unused-credential").header("alg", "HS256").subject(id.toString());
			if (!email.isEmpty()) { builder.claim("email", email); }
			SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of(), "different-name"));
			assertThat(provider.currentUser()).isEqualTo(new AuthenticatedUser(id));
		}
	}

	@Test
	void rejectsInvalidSubjectsWithoutLeakingCredentialsOrParserDetails() {
		for (String subject : new String[] {null, "", " ", "private-invalid-subject", "1-1-1-1-1"}) {
			var builder = Jwt.withTokenValue("unused-credential").header("alg", "HS256").claim("iss", "taskflow");
			if (subject != null) { builder.subject(subject); }
			SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
			assertThatThrownBy(provider::currentUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class)
					.hasMessage("Authenticated identity is unavailable.").hasNoCause();
		}
	}

	@Test
	void rejectsAbsentNonJwtAndUnauthenticatedIdentities() {
		assertThatThrownBy(provider::currentUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("unused", "unused", List.of()));
		assertThatThrownBy(provider::currentUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
		var token = new JwtAuthenticationToken(Jwt.withTokenValue("unused").header("alg", "HS256")
				.subject(UUID.randomUUID().toString()).build(), List.of());
		token.setAuthenticated(false);
		SecurityContextHolder.getContext().setAuthentication(token);
		assertThatThrownBy(provider::currentUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}
}
