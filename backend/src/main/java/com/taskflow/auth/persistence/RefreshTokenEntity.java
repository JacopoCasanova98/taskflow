package com.taskflow.auth.persistence;

import java.time.Instant;
import java.util.UUID;
import com.taskflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "refresh_tokens", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"))
public class RefreshTokenEntity extends BaseEntity {
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected RefreshTokenEntity() { }

	public RefreshTokenEntity(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getUserId() { return userId; }
	public String getTokenHash() { return tokenHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getRevokedAt() { return revokedAt; }
	public void revoke(Instant now) {
		if (revokedAt == null) { revokedAt = now; }
	}
}
