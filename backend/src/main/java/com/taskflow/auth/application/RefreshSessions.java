package com.taskflow.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.taskflow.auth.persistence.RefreshTokenEntity;
import com.taskflow.auth.persistence.RefreshTokenRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(RefreshSessionProperties.class)
public class RefreshSessions {
	private final RefreshTokenRepository sessions;
	private final RefreshSessionProperties properties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public RefreshSessions(RefreshTokenRepository sessions, RefreshSessionProperties properties, Clock clock) {
		this.sessions = sessions;
		this.properties = properties;
		this.clock = clock;
	}

	// Called inside the owning authentication operation's write transaction.
	public String issue(UUID userId, String presentedToken) {
		if (presentedToken != null && !presentedToken.isBlank()) {
			findForConsumption(presentedToken).ifPresent(session -> session.revoke(clock.instant()));
		}
		String raw = generate();
		sessions.saveAndFlush(new RefreshTokenEntity(userId, hash(raw), clock.instant().plus(properties.ttl())));
		return raw;
	}

	// All callers own a write transaction. Lock the immutable family root first so replay
	// cannot miss a descendant inserted by an overlapping rotation of another family member.
	public java.util.Optional<RefreshTokenEntity> findForConsumption(String raw) {
		String hash = hash(raw);
		var family = sessions.findFamilyIdByTokenHash(hash);
		if (family.isEmpty() || sessions.lockFamilyRoot(family.get()).isEmpty()) {
			return java.util.Optional.empty();
		}
		return sessions.findByTokenHashForUpdate(hash);
	}

	public String rotate(RefreshTokenEntity current, java.time.Instant now) {
		String raw = generate();
		var replacement = sessions.saveAndFlush(new RefreshTokenEntity(current.getUserId(), hash(raw),
				now.plus(properties.ttl()), current.getFamilyId()));
		current.markRotated(replacement.getId(), now);
		return raw;
	}

	private String generate() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.");
		}
	}
}
