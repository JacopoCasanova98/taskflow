package com.taskflow.auth.security;

import com.taskflow.user.domain.UserEmailNormalizer;
import com.taskflow.user.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TaskFlowUserDetailsService implements UserDetailsService {
	private final UserRepository users;

	public TaskFlowUserDetailsService(UserRepository users) { this.users = users; }

	@Override
	public UserDetails loadUserByUsername(String email) {
		var user = users.findByEmail(UserEmailNormalizer.normalize(email))
				.orElseThrow(() -> new UsernameNotFoundException("User not found."));
		return new TaskFlowUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}
}
