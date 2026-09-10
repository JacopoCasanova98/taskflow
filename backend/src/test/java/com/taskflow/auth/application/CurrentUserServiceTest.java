package com.taskflow.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import com.taskflow.shared.security.AuthenticatedUser;
import com.taskflow.shared.security.AuthenticatedUserProvider;
import com.taskflow.user.persistence.UserEntity;
import com.taskflow.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class CurrentUserServiceTest {
	private final UUID id = UUID.randomUUID();
	private final AuthenticatedUserProvider identity = () -> new AuthenticatedUser(id);
	private final UserRepository users = mock(UserRepository.class);
	private final CurrentUserService service = new CurrentUserService(identity, users);

	@Test
	void loadsSafeCurrentDataUsingTypedIdentity() {
		var user = new UserEntity(" CURRENT@EXAMPLE.COM ", "private-password-hash");
		ReflectionTestUtils.setField(user, "id", id);
		when(users.findById(id)).thenReturn(Optional.of(user));
		assertThat(service.currentUser()).contains(new CurrentUser(id, "current@example.com"));
		verify(users).findById(id);
		verifyNoMoreInteractions(users);
	}

	@Test
	void missingAccountHasNoInventedResult() {
		assertThat(service.currentUser()).isEmpty();
	}

	@Test
	void infrastructureFailureIsNotAnAbsentUser() {
		var failure = new DataAccessResourceFailureException("private database failure");
		when(users.findById(id)).thenThrow(failure);
		assertThatThrownBy(service::currentUser).isSameAs(failure);
	}
}
