package com.taskflow.shared.error;

import org.springframework.http.HttpStatusCode;

import java.util.Objects;

/**
 * Exception for anticipated API failures that can safely be communicated to clients.
 */
public final class ApiException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;
	private final String title;

	public ApiException(HttpStatusCode status, String code, String message) {
		this(status, code, "Request could not be completed", message);
	}

	public ApiException(HttpStatusCode status, String code, String title, String message) {
		super(message);
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.code = Objects.requireNonNull(code, "code must not be null");
		this.title = Objects.requireNonNull(title, "title must not be null");
	}

	public String getTitle() {
		return title;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
