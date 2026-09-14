package com.taskflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.DatabaseFreePersistenceTest;
import com.taskflow.auth.application.RefreshSessions;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.user.persistence.UserEntity;
import jakarta.servlet.http.Cookie;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationMvcTest extends DatabaseFreePersistenceTest {
	private static final UUID USER_ID = UUID.fromString("a5244ce1-c247-47ae-9e5c-c74d0d3092b9");
	private static final String PASSWORD = "  correct password with spaces  ";
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private PasswordEncoder encoder;
	@Autowired private JwtDecoder decoder;

	@BeforeEach
	void persistence() {
		when(sessions.findFamilyIdByTokenHash(any())).thenReturn(Optional.of(UUID.randomUUID()));
		when(sessions.lockFamilyRoot(any())).thenReturn(Optional.of(new RefreshTokenEntity(USER_ID, "unused", java.time.Instant.MAX)));
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(users.saveAndFlush(any())).thenAnswer(invocation -> {
			UserEntity user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", USER_ID); // Stand in for persistence-generated identity.
			return user;
		});
	}

	@Test
	void csrfBootstrapIsPublicAndSetsAngularCookiePolicy() throws Exception {
		var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
				.andExpect(content().string("")).andReturn();
		Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getSecure()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
		assertThat(cookie.getDomain()).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	void publicPostsStillRequireValidCsrfAndDoNotReachPersistence() throws Exception {
		for (String path : new String[] {"register", "login", "refresh", "logout"}) {
			mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
					.content(json("user@example.com", PASSWORD)))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
			Cookie csrf = csrf();
			mvc.perform(post("/api/auth/" + path).cookie(csrf).header("X-XSRF-TOKEN", "wrong")
					.contentType(MediaType.APPLICATION_JSON).content(json("user@example.com", PASSWORD)))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
		verifyNoInteractions(users, sessions);
	}

	@Test
	void registrationPersistsNormalizedUserAndHashOnlyRefreshSessionAndRotatesCsrf() throws Exception {
		Cookie csrf = csrf();
		var result = mvc.perform(request("register", "  USER@EXAMPLE.COM  ", PASSWORD, csrf))
				.andExpect(status().isCreated()).andReturn();
		assertSuccess(result);
		var saved = ArgumentCaptor.forClass(UserEntity.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
		assertThat(saved.getValue().getPasswordHash()).startsWith("{argon2id}").isNotEqualTo(PASSWORD);
		assertThat(encoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
		var session = ArgumentCaptor.forClass(RefreshTokenEntity.class);
		verify(sessions).saveAndFlush(session.capture());
		String raw = result.getResponse().getCookie("TASKFLOW_REFRESH").getValue();
		assertThat(session.getValue().getTokenHash()).isEqualTo(RefreshSessions.hash(raw)).isNotEqualTo(raw);
		assertThat(session.getValue().getUserId()).isEqualTo(USER_ID);
		Cookie fresh = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(fresh.getValue()).isNotEqualTo(csrf.getValue());
		// The fresh cookie/header pair works immediately, without a page reload.
		mvc.perform(request("login", "missing@example.com", PASSWORD, fresh))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void registrationValidatesPasswordAndEmailLengthsWithoutEchoingPassword() throws Exception {
		for (String password : new String[] {"x".repeat(14), "x".repeat(129)}) {
			var result = mvc.perform(request("register", "user@example.com", password, csrf()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain(password);
		}
		mvc.perform(request("register", "a".repeat(243) + "@example.com", PASSWORD, csrf()))
				.andExpect(status().isBadRequest());
		verify(users, never()).saveAndFlush(any());
		mvc.perform(request("register", "user@example.com", "x".repeat(128), csrf()))
				.andExpect(status().isCreated());
	}

	@Test
	void existingEmailAndDatabaseRaceHaveSameConflict() throws Exception {
		when(users.existsByEmail("user@example.com")).thenReturn(true);
		var existing = mvc.perform(request("register", "USER@example.com", PASSWORD, csrf()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED")).andReturn();
		when(users.existsByEmail("user@example.com")).thenReturn(false);
		doThrow(new DataIntegrityViolationException("database internals",
				new ConstraintViolationException("duplicate", new SQLException("secret SQL", "23505"), "insert", "uk_users_email")))
				.when(users).saveAndFlush(any());
		var race = mvc.perform(request("register", "user@example.com", PASSWORD, csrf()))
				.andExpect(status().isConflict()).andReturn();
		assertThat(race.getResponse().getContentAsString()).isEqualTo(existing.getResponse().getContentAsString());
		verify(transactionManager).rollback(any());
		verifyNoInteractions(sessions);
	}

	@Test
	void validLoginUsesRealCredentialPipelineAndReturnsTokensForUuid() throws Exception {
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
		Cookie old = csrf();
		var result = mvc.perform(request("login", " USER@EXAMPLE.COM ", PASSWORD, old))
				.andExpect(status().isOk()).andReturn();
		assertSuccess(result);
		assertThat(result.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotEqualTo(old.getValue());
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void unknownEmailAndWrongPasswordHaveIdenticalSafePublicFailure() throws Exception {
		var unknown = mvc.perform(request("login", "missing@example.com", PASSWORD, csrf()))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.title").value("Authentication failed"))
				.andExpect(jsonPath("$.detail").value("Invalid email or password.")).andReturn();
		UserEntity user = user();
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		var wrong = mvc.perform(request("login", "user@example.com", "incorrect password", csrf()))
				.andExpect(status().isUnauthorized()).andReturn();
		assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString())
				.doesNotContain(PASSWORD, "incorrect password", user.getPasswordHash(), "BadCredentialsException", "trace");
		verifyNoInteractions(sessions);
	}

	@Test
	void loginReplacesPresentedRefreshSessionAndAcceptsMinimumRegistrationPassword() throws Exception {
		mvc.perform(request("register", "user@example.com", "x".repeat(15), csrf())).andExpect(status().isCreated());
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
		var old = new RefreshTokenEntity(USER_ID, RefreshSessions.hash("old-cookie"), java.time.Instant.now().plusSeconds(600));
		when(sessions.findByTokenHashForUpdate(RefreshSessions.hash("old-cookie"))).thenReturn(Optional.of(old));
		mvc.perform(request("login", "user@example.com", PASSWORD, csrf()).cookie(new Cookie("TASKFLOW_REFRESH", "old-cookie")))
				.andExpect(status().isOk());
		assertThat(old.getRevokedAt()).isNotNull();
	}

	@Test
	void infrastructureFailureRemainsSafe500() throws Exception {
		when(users.findByEmail(any())).thenThrow(new DataAccessResourceFailureException("internal database failure"));
		var result = mvc.perform(request("login", "user@example.com", PASSWORD, csrf()))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("internal database", PASSWORD, "INVALID_CREDENTIALS");
	}

	@Test
	void refreshPersistenceFailureRollsBackRegistration() throws Exception {
		when(sessions.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("database failure"));
		mvc.perform(request("register", "user@example.com", PASSWORD, csrf())).andExpect(status().isInternalServerError());
		verify(transactionManager).rollback(any());
		verify(transactionManager, never()).commit(any());
	}

	@Test
	void deferredEndpointsRemainProtected() throws Exception {
		for (String path : new String[] {"refresh", "logout"}) {
			mvc.perform(get("/api/auth/" + path)).andExpect(status().isUnauthorized());
		}
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRotatesAndReturnsExistingSafeAuthenticationContract() throws Exception {
		String raw = "a".repeat(43);
		var old = new RefreshTokenEntity(USER_ID, RefreshSessions.hash(raw), java.time.Instant.now().plusSeconds(600));
		when(sessions.findByTokenHashForUpdate(RefreshSessions.hash(raw))).thenReturn(Optional.of(old));
		when(users.findById(USER_ID)).thenReturn(Optional.of(user()));
		when(sessions.saveAndFlush(any())).thenAnswer(invocation -> {
			RefreshTokenEntity token = invocation.getArgument(0);
			ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
			return token;
		});
		var result = mvc.perform(request("refresh", "unused", "unused", csrf())
				.cookie(new Cookie("TASKFLOW_REFRESH", raw))).andExpect(status().isOk()).andReturn();
		assertSuccess(result);
		var saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
		verify(sessions).saveAndFlush(saved.capture());
		assertThat(old.getReplacedByTokenId()).isEqualTo(saved.getValue().getId());
		assertThat(old.isRevoked()).isTrue();
		assertThat(saved.getValue().getFamilyId()).isEqualTo(old.getFamilyId());
		assertThat(result.getResponse().getCookie("TASKFLOW_REFRESH").getValue()).isNotEqualTo(raw);
		verify(transactionManager).commit(any());
	}

	@Test
	void invalidRefreshStatesShareExactlyOneSafeContractAndClearCookie() throws Exception {
		String expected = null;
		for (String state : new String[] {"missing", "malformed", "unknown", "expired", "revoked", "replay", "missing-user"}) {
			String raw = "b".repeat(43);
			var token = new RefreshTokenEntity(USER_ID, RefreshSessions.hash(raw),
					java.time.Instant.now().plusSeconds(state.equals("expired") ? -60 : 600));
			if (state.equals("revoked")) { token.revoke(java.time.Instant.now()); }
			if (state.equals("replay")) { token.markRotated(UUID.randomUUID(), java.time.Instant.now()); }
			when(sessions.findByTokenHashForUpdate(any())).thenReturn(
					state.equals("unknown") ? Optional.empty() : Optional.of(token));
			var request = request("refresh", "unused", "unused", csrf());
			if (!state.equals("missing")) { request.cookie(new Cookie("TASKFLOW_REFRESH", state.equals("malformed") ? "bad" : raw)); }
			var result = mvc.perform(request).andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("SESSION_INVALID"))
					.andExpect(jsonPath("$.title").value("Session unavailable"))
					.andExpect(jsonPath("$.detail").value("Your session is no longer valid. Please sign in again.")).andReturn();
			String body = result.getResponse().getContentAsString();
			if (expected == null) { expected = body; }
			assertThat(body).isEqualTo(expected).doesNotContain(raw, token.getTokenHash(), USER_ID.toString());
			assertCleared(result);
		}
	}

	@Test
	void logoutIsIdempotentAndRenewsAnonymousCsrfForImmediateLogin() throws Exception {
		for (String state : new String[] {"active", "missing", "malformed", "unknown", "expired", "revoked"}) {
			String raw = "c".repeat(43);
			var token = new RefreshTokenEntity(USER_ID, RefreshSessions.hash(raw),
					java.time.Instant.now().plusSeconds(state.equals("expired") ? -60 : 600));
			if (state.equals("revoked")) { token.revoke(java.time.Instant.now().minusSeconds(60)); }
			when(sessions.findByTokenHashForUpdate(any())).thenReturn(state.equals("unknown") ? Optional.empty() : Optional.of(token));
			Cookie oldCsrf = csrf();
			var request = request("logout", "unused", "unused", oldCsrf);
			if (!state.equals("missing")) { request.cookie(new Cookie("TASKFLOW_REFRESH", state.equals("malformed") ? "bad" : raw)); }
			var result = mvc.perform(request).andExpect(status().isNoContent()).andExpect(content().string("")).andReturn();
			assertCleared(result);
			if (state.equals("active")) { assertThat(token.isRevoked()).isTrue(); }
			var csrfCookies = java.util.Arrays.stream(result.getResponse().getCookies())
					.filter(cookie -> cookie.getName().equals("XSRF-TOKEN")).toList();
			assertThat(csrfCookies).anyMatch(cookie -> cookie.getMaxAge() == 0);
			Cookie fresh = csrfCookies.getLast();
			assertThat(fresh.getValue()).isNotBlank().isNotEqualTo(oldCsrf.getValue());
			mvc.perform(request("login", "missing@example.com", PASSWORD, fresh))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}
	}

	@Test
	void refreshAndLogoutDatabaseFailuresRemain500() throws Exception {
		when(sessions.findByTokenHashForUpdate(any())).thenThrow(new DataAccessResourceFailureException("private failure"));
		for (String path : new String[] {"refresh", "logout"}) {
			var result = mvc.perform(request(path, "unused", "unused", csrf()).cookie(new Cookie("TASKFLOW_REFRESH", "d".repeat(43))))
					.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain("private failure", "SESSION_INVALID");
		}
	}

	@Test
	void logoutLeavesIssuedAccessJwtUsableAndGetLifecycleRoutesUnimplemented() throws Exception {
		var registration = mvc.perform(request("register", "user@example.com", PASSWORD, csrf()))
				.andExpect(status().isCreated()).andReturn();
		String jwt = mapper.readTree(registration.getResponse().getContentAsString()).get("accessToken").asText();
		mvc.perform(request("logout", "unused", "unused", csrf())
				.cookie(registration.getResponse().getCookie("TASKFLOW_REFRESH"))).andExpect(status().isNoContent());
		mvc.perform(get("/api/security-check").header("Authorization", "Bearer " + jwt))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		for (String path : new String[] {"refresh", "logout"}) {
			mvc.perform(get("/api/auth/" + path).header("Authorization", "Bearer " + jwt))
					.andExpect(status().isMethodNotAllowed());
		}
	}

	private void assertCleared(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("TASKFLOW_REFRESH");
		assertThat(cookie.getValue()).isEmpty();
		assertThat(cookie.getMaxAge()).isZero();
		assertThat(cookie.getPath()).isEqualTo("/api/auth");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isFalse();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
		assertThat(cookie.getDomain()).isNull();
	}

	private UserEntity user() {
		UserEntity user = new UserEntity("user@example.com", encoder.encode(PASSWORD));
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	private String json(String email, String password) throws Exception {
		return mapper.writeValueAsString(Map.of("email", email, "password", password));
	}

	private MockHttpServletRequestBuilder request(String path, String email, String password, Cookie csrf) throws Exception {
		return post("/api/auth/" + path).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(json(email, password));
	}

	private void assertSuccess(MvcResult result) throws Exception {
		var body = mapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.size()).isEqualTo(3);
		assertThat(body.get("user").size()).isEqualTo(2);
		assertThat(body.get("user").get("id").asText()).isEqualTo(USER_ID.toString());
		assertThat(body.get("user").get("email").asText()).isEqualTo("user@example.com");
		var jwt = decoder.decode(body.get("accessToken").asText());
		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
		assertThat(jwt.getExpiresAt().toString()).isEqualTo(body.get("accessTokenExpiresAt").asText());
		Cookie refresh = result.getResponse().getCookie("TASKFLOW_REFRESH");
		assertThat(refresh).isNotNull();
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getSecure()).isFalse();
		assertThat(refresh.getPath()).isEqualTo("/api/auth");
		assertThat(refresh.getAttribute("SameSite")).isEqualTo("Strict");
		assertThat(refresh.getDomain()).isNull();
		assertThat(refresh.getMaxAge()).isEqualTo(2592000);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(refresh.getValue(), PASSWORD, "passwordHash", "tokenHash");
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	void authenticationEventsContainSafeIdentityWithoutCredentialsOrEmail() throws Exception {
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
		try (var logs = new com.taskflow.shared.logging.LogCapture(com.taskflow.auth.application.AuthenticationService.class)) {
			var success = mvc.perform(request("login", "user@example.com", PASSWORD, csrf()))
					.andExpect(status().isOk()).andReturn();
			mvc.perform(request("login", "missing@example.com", PASSWORD, csrf()))
					.andExpect(status().isUnauthorized());
			assertThat(logs.events()).hasSize(2);
			assertThat(logs.events().get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
			assertThat(logs.events().get(0).getFormattedMessage())
					.isEqualTo("event=authentication_succeeded userId=" + USER_ID);
			assertThat(logs.events().get(1).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
			assertThat(logs.events().get(1).getFormattedMessage())
					.isEqualTo("event=authentication_failed reason=invalid_credentials");
			assertThat(logs.messages()).doesNotContain(PASSWORD, "user@example.com", "missing@example.com",
					success.getResponse().getCookie("TASKFLOW_REFRESH").getValue());
			assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
		}
	}
}
