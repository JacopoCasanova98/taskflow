package com.taskflow.auth.application;

import java.util.UUID;

/** Safe current-user application result, independent of HTTP and persistence entities. */
public record CurrentUser(UUID id, String email) { }
