package com.taskflow.shared.error;

import org.springframework.http.HttpStatusCode;

import java.util.Objects;

/**
 * Base exception for anticipated API failures that can safely be communicated to clients.
 */
public class ApiException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;

	public ApiException(HttpStatusCode status, String code, String message) {
		super(message);
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.code = Objects.requireNonNull(code, "code must not be null");
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
