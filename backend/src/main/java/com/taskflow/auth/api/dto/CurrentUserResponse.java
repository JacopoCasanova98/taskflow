package com.taskflow.auth.api.dto;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email) { }
