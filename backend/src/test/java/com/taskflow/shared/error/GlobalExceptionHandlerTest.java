package com.taskflow.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	@Test
	void returnsStableProblemDetailForExpectedApiFailures() {
		ProblemDetail problem = exceptionHandler.handleApiException(
				new ApiException(HttpStatus.CONFLICT, "TASK_STATE_CONFLICT", "The task cannot be completed."),
				request()
		);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getTitle()).isEqualTo("Request could not be completed");
		assertThat(problem.getDetail()).isEqualTo("The task cannot be completed.");
		assertThat(problem.getType()).hasToString("urn:taskflow:problem:task_state_conflict");
		assertThat(problem.getProperties()).containsEntry("code", "TASK_STATE_CONFLICT");
		assertThat(problem.getInstance()).hasToString("/api/tasks/42");
	}

	@Test
	@SuppressWarnings("unchecked")
	void returnsFieldViolationsForBindingFailures() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError(
				"request",
				"title",
				null,
				false,
				new String[]{"NotBlank"},
				null,
				"must not be blank"
		));

		ProblemDetail problem = exceptionHandler.handleBindException(
				new org.springframework.validation.BindException(bindingResult),
				request()
		);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
		List<FieldViolation> violations = (List<FieldViolation>) problem.getProperties().get("violations");
		assertThat(violations).containsExactly(new FieldViolation("title", "must not be blank", "NotBlank"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() {
		ProblemDetail problem = exceptionHandler.handleUnexpectedException(
				new IllegalStateException("database password is secret"),
				request()
		);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
		assertThat(problem.getDetail()).doesNotContain("database password");
	}

	@Test
	void hidesBindingFailureDetailsAndUsesGenericConstraintCode() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError("request", "count", "secret-input", true,
				new String[]{"typeMismatch.request.count", "typeMismatch"}, null,
				"Cannot convert secret-input to internal class"));
		ProblemDetail problem = exceptionHandler.handleBindException(
				new org.springframework.validation.BindException(bindingResult), request());
		assertThat(problem.getProperties()).containsEntry("violations",
				List.of(new FieldViolation("count", "Invalid value.", "typeMismatch")));
	}

	@Test
	void treatsReturnValueValidationAsSafeServerFailure() {
		var exception = org.mockito.Mockito.mock(
				org.springframework.web.method.annotation.HandlerMethodValidationException.class);
		org.mockito.Mockito.when(exception.isForReturnValue()).thenReturn(true);
		org.mockito.Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
		ProblemDetail problem = exceptionHandler.handleHandlerMethodValidationException(exception, request());
		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR")
				.doesNotContainKey("violations");
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("POST");
		request.setRequestURI("/api/tasks/42");
		return request;
	}

	@Test
	void unexpectedErrorHasOneCorrelatedServerStackAndSafePublicContract() {
		try (var logs = new com.taskflow.shared.logging.LogCapture(GlobalExceptionHandler.class)) {
			org.slf4j.MDC.put("requestId", "550e8400-e29b-41d4-a716-446655440000");
			var problem = exceptionHandler.handleUnexpectedException(new IllegalStateException("deterministic failure"), request());
			assertThat(logs.events()).hasSize(1);
			var event = logs.events().getFirst();
			assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
			assertThat(event.getFormattedMessage()).isEqualTo("event=unhandled_exception");
			assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
			assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
			assertThat(event.getMDCPropertyMap()).containsKey("requestId");
			assertThat(problem.getStatus()).isEqualTo(500);
			assertThat(problem.getProperties()).containsOnlyKeys("code").containsEntry("code", "INTERNAL_ERROR");
			assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
		} finally { org.slf4j.MDC.remove("requestId"); }
	}

	@Test
	void expectedNotFoundDoesNotLogUnexpectedError() {
		try (var logs = new com.taskflow.shared.logging.LogCapture(GlobalExceptionHandler.class)) {
			exceptionHandler.handleApiException(new ApiException(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "Board not found"), request());
			assertThat(logs.events()).isEmpty();
		}
	}

}
