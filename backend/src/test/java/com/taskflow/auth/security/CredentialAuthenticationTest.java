package com.taskflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class CredentialAuthenticationTest {
	@Test
	void authenticatesNormalizedEmailAndErasesPrincipalCredentialsWithoutRoles() {
		var users = mock(UserRepository.class);
		var encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
		var user = new UserEntity("user@example.com", encoder.encode("a long correct password"));
		UUID id = UUID.randomUUID();
		ReflectionTestUtils.setField(user, "id", id);
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		var manager = new CredentialAuthenticationConfiguration()
				.credentialAuthenticationManager(new TaskFlowUserDetailsService(users), encoder);
		var authentication = manager.authenticate(UsernamePasswordAuthenticationToken
				.unauthenticated(" USER@EXAMPLE.COM ", "a long correct password"));
		var principal = (TaskFlowUserPrincipal) authentication.getPrincipal();
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getCredentials()).isNull();
		assertThat(principal.getPassword()).isNull();
		assertThat(principal.getUserId()).isEqualTo(id);
		assertThat(principal.getAuthorities()).isEmpty();
		assertThat(user.getPasswordHash()).isNotNull();
	}
}
