package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.taskflow.auth.security.TaskFlowUserPrincipal;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.jwt.AccessToken;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AuthenticationServiceTest {
	private static final UUID USER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
	private static final AccessToken ACCESS = new AccessToken("test-access", Instant.parse("2026-09-14T12:15:00Z"));
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwords = mock(PasswordEncoder.class);
	private final AuthenticationManager authentication = mock(AuthenticationManager.class);
	private final AccessTokenService access = mock(AccessTokenService.class);
	private final RefreshSessions refresh = mock(RefreshSessions.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final AuthenticationService service = new AuthenticationService(users, passwords, authentication, access, refresh, transactions);

	@Test
	void registrationNormalizesEmailPreservesPasswordAndIssuesCredentialsForPersistedIdentity() {
		transaction();
		when(passwords.encode("  password  ")).thenReturn("encoded-hash");
		when(users.saveAndFlush(any())).thenAnswer(call -> {
			UserEntity user = call.getArgument(0);
			ReflectionTestUtils.setField(user, "id", USER);
			return user;
		});
		when(access.issue(USER)).thenReturn(ACCESS);
		when(refresh.issue(USER, null)).thenReturn("test-refresh");

		var result = service.register(" USER@EXAMPLE.COM ", "  password  ");

		assertThat(result).isEqualTo(new AuthenticationResult(USER, "user@example.com", ACCESS, "test-refresh"));
		var saved = ArgumentCaptor.forClass(UserEntity.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-hash");
		verify(users).existsByEmail("user@example.com");
		verify(passwords).encode("  password  ");
		verifyNoInteractions(authentication);
	}

	@Test
	void duplicateEmailStopsBeforeEncodingOrIssuingCredentials() {
		when(users.existsByEmail("user@example.com")).thenReturn(true);

		assertDuplicate(() -> service.register(" USER@EXAMPLE.COM ", "password"));

		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(passwords, access, refresh, transactions, authentication);
	}

	@Test
	void nestedEmailConstraintFailureMapsToDuplicateRegistration() {
		transaction();
		var constraint = new ConstraintViolationException("constraint", new SQLException("failure"), "uk_users_email");
		doThrow(new DataIntegrityViolationException("write failed", new IllegalStateException(constraint)))
				.when(users).saveAndFlush(any());

		assertDuplicate(() -> service.register("user@example.com", "password"));

		verifyNoInteractions(access, refresh);
	}

	@ParameterizedTest
	@ValueSource(strings = {"other_constraint", "no_constraint"})
	void unrelatedIntegrityFailureIsNotMisreportedAsDuplicateEmail(String kind) {
		transaction();
		Throwable cause = kind.equals("other_constraint")
				? new ConstraintViolationException("constraint", new SQLException("failure"), "other_constraint")
				: new IllegalStateException("unrelated failure");
		var failure = new DataIntegrityViolationException("write failed", cause);
		doThrow(failure).when(users).saveAndFlush(any());

		assertThatThrownBy(() -> service.register("user@example.com", "password")).isSameAs(failure);

		verifyNoInteractions(access, refresh);
	}

	@Test
	void loginNormalizesIdentityErasesCredentialsAndReplacesPresentedSession() {
		transaction();
		var principal = new TaskFlowUserPrincipal(USER, "user@example.com", "stored-hash");
		when(authentication.authenticate(any())).thenAnswer(call -> {
			Authentication request = call.getArgument(0);
			assertThat(request.getName()).isEqualTo("user@example.com");
			assertThat(request.getCredentials()).isEqualTo("  password  ");
			return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
		});
		when(access.issue(USER)).thenReturn(ACCESS);
		when(refresh.issue(USER, "presented-session")).thenReturn("replacement-session");

		var result = service.login(" USER@EXAMPLE.COM ", "  password  ", "presented-session");

		assertThat(result).isEqualTo(new AuthenticationResult(USER, "user@example.com", ACCESS, "replacement-session"));
		assertThat(principal.getPassword()).isNull();
		assertErasedRequest();
		verifyNoInteractions(users, passwords);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void failedAuthenticationErasesRequestAndNeverIssuesCredentials(boolean infrastructureFailure) {
		RuntimeException failure = infrastructureFailure
				? new AuthenticationServiceException("provider unavailable")
				: new BadCredentialsException("Invalid credentials");
		when(authentication.authenticate(any())).thenThrow(failure);

		assertThatThrownBy(() -> service.login(" USER@EXAMPLE.COM ", "password", "presented-session"))
				.isSameAs(failure);

		assertErasedRequest();
		verifyNoInteractions(users, passwords, access, refresh, transactions);
	}

	private void assertErasedRequest() {
		var request = ArgumentCaptor.forClass(Authentication.class);
		verify(authentication).authenticate(request.capture());
		assertThat(request.getValue().getCredentials()).isNull();
	}

	private void transaction() {
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	private static void assertDuplicate(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
	}
}
