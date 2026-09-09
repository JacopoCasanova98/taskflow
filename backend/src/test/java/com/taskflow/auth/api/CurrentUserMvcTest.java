package com.taskflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.DatabaseFreePersistenceTest;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.shared.security.jwt.JwtProperties;
import com.taskflow.user.persistence.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentUserMvcTest extends DatabaseFreePersistenceTest {
	private static final UUID ID = UUID.fromString("fde86a87-1c1b-4b35-88fb-cc8d77df9b85");
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private AccessTokenService tokens;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;
	@Autowired private Clock clock;

	@Test
	void noBearerRequiresAuthenticationBeforePersistence() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(users, sessions);
	}

	@Test
	void validBearerReturnsOnlyCurrentSafeUserWithoutRefreshCookieOrCsrf() throws Exception {
		var user = new UserEntity(" CURRENT@EXAMPLE.COM ", "private-password-hash");
		ReflectionTestUtils.setField(user, "id", ID);
		when(users.findById(ID)).thenReturn(Optional.of(user));
		String jwt = tokens.issue(ID).value();
		var result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
				.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.email").value("current@example.com")).andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(mapper.readTree(body).size()).isEqualTo(2);
		assertThat(body).doesNotContain(jwt, "private-password-hash", "passwordHash", "token", "refresh", "createdAt", "updatedAt", "roles");
		verify(users).findById(ID);
		verifyNoInteractions(sessions);
	}

	@Test
	void deletedAccountReturnsSafeSessionInvalidRatherThan404() throws Exception {
		var result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokens.issue(ID).value()))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("SESSION_INVALID"))
				.andExpect(jsonPath("$.title").value("Session unavailable"))
				.andExpect(jsonPath("$.detail").value("Your session is no longer valid. Please sign in again.")).andReturn();
		assertThat(mapper.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(6);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(ID.toString(), "database", "UserEntity");
	}

	@Test
	void invalidSubjectsAreRejectedByResourceServerBeforeCurrentUserLookup() throws Exception {
		for (String subject : new String[] {null, "", " ", "private-invalid-subject", "1-1-1-1-1"}) {
			var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
					.issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(900));
			if (subject != null) { claims.subject(subject); }
			String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
			var result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
					.andExpect(header().string("WWW-Authenticate", "Bearer")).andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain(jwt, "private-invalid-subject", "1-1-1-1-1", "Invalid subject", "Exception");
		}
		verifyNoInteractions(users, sessions);
	}

	@Test
	void databaseFailureRemainsSafe500() throws Exception {
		when(users.findById(ID)).thenThrow(new DataAccessResourceFailureException("private database details"));
		var result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokens.issue(ID).value()))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private database details", ID.toString(), "SESSION_INVALID");
	}
}
