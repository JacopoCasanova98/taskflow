package com.taskflow.auth.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.Objects;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefreshSessionLifecycle {
	private static final Logger LOG = LoggerFactory.getLogger(RefreshSessionLifecycle.class);
	private final RefreshTokenRepository sessions;
	private final RefreshSessions tokens;
	private final UserRepository users;
	private final AccessTokenService accessTokens;
	private final Clock clock;
	private final TransactionTemplate transactions;

	public RefreshSessionLifecycle(RefreshTokenRepository sessions, RefreshSessions tokens, UserRepository users,
			AccessTokenService accessTokens, Clock clock, PlatformTransactionManager transactionManager) {
		this.sessions = sessions;
		this.tokens = tokens;
		this.users = users;
		this.accessTokens = accessTokens;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public Optional<AuthenticationResult> refresh(String raw) {
		if (!wellFormed(raw)) { return Optional.empty(); }
		// Invalid outcomes return normally so replay revocation commits before HTTP 401.
		Optional<AuthenticationResult> result = Objects.requireNonNull(transactions.execute(status -> {
			var found = tokens.findForConsumption(raw);
			if (found.isEmpty()) { return Optional.empty(); }
			var current = found.get();
			var now = clock.instant();
			if (current.isRevoked()) {
				if (current.getReplacedByTokenId() != null) {
					sessions.revokeActiveByFamilyId(current.getFamilyId(), now);
				}
				return Optional.empty();
			}
			if (current.isExpired(now)) { return Optional.empty(); }
			var user = users.findById(current.getUserId());
			if (user.isEmpty()) { return Optional.empty(); }
			String replacement = tokens.rotate(current, now);
			return Optional.of(new AuthenticationResult(user.get().getId(), user.get().getEmail(),
					accessTokens.issue(current.getUserId()), replacement));
		}));
		result.ifPresent(session -> LOG.debug("event=session_refreshed userId={}", session.userId()));
		return result;
	}

	public void logout(String raw) {
		if (!wellFormed(raw)) {
			LOG.info("event=session_logged_out");
			return;
		}
		transactions.executeWithoutResult(status -> tokens.findForConsumption(raw)
				.ifPresent(token -> token.revoke(clock.instant())));
		LOG.info("event=session_logged_out");
	}

	private boolean wellFormed(String raw) {
		return raw != null && raw.matches("[A-Za-z0-9_-]{43}");
	}
}
