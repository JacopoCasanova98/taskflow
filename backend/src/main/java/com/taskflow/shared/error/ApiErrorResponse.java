package com.taskflow.shared.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Client-safe representation of an API error.
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String errorCode,
		String message,
		String path,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) List<ApiFieldError> fieldErrors
) {

	public ApiErrorResponse {
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}
}
