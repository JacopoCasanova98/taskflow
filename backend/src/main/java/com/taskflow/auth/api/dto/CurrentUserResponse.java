package com.taskflow.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record CurrentUserResponse(@Schema(example = "8bf24ae0-bd7e-4c3e-b226-38ce5db44b35") UUID id,
		@Schema(example = "alex@example.test", format = "email") String email) { }
