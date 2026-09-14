package com.taskflow.auth.application;

import com.taskflow.auth.security.TaskFlowUserPrincipal;
import com.taskflow.shared.error.ApiException;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.user.domain.UserEmailNormalizer;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthenticationService {
	private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
	private final UserRepository users;
	private final PasswordEncoder passwords;
	private final AuthenticationManager authenticationManager;
	private final AccessTokenService tokens;
	private final RefreshSessions refreshSessions;
	private final TransactionTemplate transactions;

	public AuthenticationService(UserRepository users, PasswordEncoder passwords, AuthenticationManager authenticationManager,
			AccessTokenService tokens, RefreshSessions refreshSessions, PlatformTransactionManager transactionManager) {
		this.users = users;
		this.passwords = passwords;
		this.authenticationManager = authenticationManager;
		this.tokens = tokens;
		this.refreshSessions = refreshSessions;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public AuthenticationResult register(String email, String password) {
		String normalized = UserEmailNormalizer.normalize(email);
		if (users.existsByEmail(normalized)) { throw duplicateEmail(); }
		String hash = passwords.encode(password);
		try {
			var result = transactions.execute(status -> {
				UserEntity user = users.saveAndFlush(new UserEntity(normalized, hash));
				var access = tokens.issue(user.getId());
				String refresh = refreshSessions.issue(user.getId(), null);
				return new AuthenticationResult(user.getId(), user.getEmail(), access, refresh);
			});
			LOG.info("event=user_registered userId={}", result.userId());
			return result;
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException constraint
						&& "uk_users_email".equals(constraint.getConstraintName())) {
					throw duplicateEmail();
				}
			}
			throw exception;
		}
	}

	public AuthenticationResult login(String email, String password, String presentedRefreshToken) {
		var request = UsernamePasswordAuthenticationToken.unauthenticated(UserEmailNormalizer.normalize(email), password);
		TaskFlowUserPrincipal principal;
		try {
			principal = (TaskFlowUserPrincipal) authenticationManager.authenticate(request).getPrincipal();
		} catch (BadCredentialsException exception) {
			LOG.warn("event=authentication_failed reason=invalid_credentials");
			throw exception;
		} finally {
			request.eraseCredentials();
		}
		principal.eraseCredentials();
		var access = tokens.issue(principal.getUserId());
		var result = transactions.execute(status -> new AuthenticationResult(principal.getUserId(), principal.getUsername(),
				access, refreshSessions.issue(principal.getUserId(), presentedRefreshToken)));
		LOG.info("event=authentication_succeeded userId={}", result.userId());
		return result;
	}

	private ApiException duplicateEmail() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
	}
}
