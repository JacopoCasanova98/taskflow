package com.taskflow.auth.application;

import java.util.Optional;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.user.persistence.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
	private final AuthenticatedUserProvider identity;
	private final UserRepository users;

	public CurrentUserService(AuthenticatedUserProvider identity, UserRepository users) {
		this.identity = identity;
		this.users = users;
	}

	public Optional<CurrentUser> currentUser() {
		return users.findById(identity.currentUser().id()).map(user -> new CurrentUser(user.getId(), user.getEmail()));
	}
}
