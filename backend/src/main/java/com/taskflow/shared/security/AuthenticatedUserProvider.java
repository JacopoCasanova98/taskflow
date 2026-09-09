package com.taskflow.shared.security;

public interface AuthenticatedUserProvider {
	AuthenticatedUser currentUser();
}
