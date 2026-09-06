package com.taskflow.auth.api.dto;

import com.taskflow.user.domain.UserEmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
		@NotNull @Size(min = 15, max = 128) String password) {
	public RegisterRequest {
		if (email != null) { email = UserEmailNormalizer.normalize(email); }
	}
	@Override
	public String toString() { return "RegisterRequest[credentials=REDACTED]"; }
}
