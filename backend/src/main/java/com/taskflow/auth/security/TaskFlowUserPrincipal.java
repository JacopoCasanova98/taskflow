package com.taskflow.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class TaskFlowUserPrincipal implements UserDetails, CredentialsContainer {
	private final UUID userId;
	private final String email;
	private String passwordHash;

	public TaskFlowUserPrincipal(UUID userId, String email, String passwordHash) {
		this.userId = userId;
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public UUID getUserId() { return userId; }
	@Override
	public String getUsername() { return email; }
	@Override
	public String getPassword() { return passwordHash; }
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
	@Override
	public void eraseCredentials() { passwordHash = null; }
}
