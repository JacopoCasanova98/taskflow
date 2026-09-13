package com.taskflow.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 254) @Schema(example = "alex@example.test", format = "email") String email,
		@NotEmpty @Size(max = 128) @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {
	@Override
	public String toString() { return "LoginRequest[credentials=REDACTED]"; }
}
