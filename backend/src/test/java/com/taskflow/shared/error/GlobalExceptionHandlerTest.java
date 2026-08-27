package com.taskflow.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void returnsValidationErrorWithSortedFieldErrors() throws NoSuchMethodException {
		MethodParameter parameter = new MethodParameter(
				ValidationEndpoint.class.getDeclaredMethod("create", ValidationRequest.class),
				0
		);
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ValidationRequest(), "request");
		bindingResult.rejectValue("title", "NotBlank", "must not be blank");
		bindingResult.rejectValue("description", "Size", "size must be between 1 and 500");

		ResponseEntity<ApiErrorResponse> response = handler.handleValidation(
				new MethodArgumentNotValidException(parameter, bindingResult),
				request()
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody())
				.extracting(ApiErrorResponse::status, ApiErrorResponse::errorCode, ApiErrorResponse::path)
				.containsExactly(400, "VALIDATION_ERROR", "/api/test");
		assertThat(response.getBody().fieldErrors())
				.extracting(ApiFieldError::field, ApiFieldError::message)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("description", "size must be between 1 and 500"),
						org.assertj.core.groups.Tuple.tuple("title", "must not be blank")
				);
	}

	@Test
	void returnsNotFoundError() {
		ResponseEntity<ApiErrorResponse> response = handler.handleResourceNotFound(
				new ResourceNotFoundException("sensitive internal detail"),
				request()
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody())
				.extracting(ApiErrorResponse::status, ApiErrorResponse::errorCode, ApiErrorResponse::message)
				.containsExactly(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.");
	}

	@Test
	void returnsConflictError() {
		ResponseEntity<ApiErrorResponse> response = handler.handleResourceConflict(
				new ResourceConflictException("sensitive internal detail"),
				request()
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody())
				.extracting(ApiErrorResponse::status, ApiErrorResponse::errorCode, ApiErrorResponse::message)
				.containsExactly(409, "RESOURCE_CONFLICT", "The request conflicts with the current resource state.");
	}

	@Test
	void returnsSafeInternalServerError() {
		ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
				new IllegalStateException("database password is secret"),
				request()
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
				.extracting(ApiErrorResponse::status, ApiErrorResponse::errorCode, ApiErrorResponse::message)
				.containsExactly(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.");
		assertThat(response.getBody().message()).doesNotContain("database password is secret");
	}

	private MockHttpServletRequest request() {
		return new MockHttpServletRequest("POST", "/api/test");
	}

	private static final class ValidationEndpoint {

		void create(@Valid ValidationRequest request) {
		}
	}

	private static final class ValidationRequest {

		private final String description = "";
		private final String title = "";

		public String getDescription() {
			return description;
		}

		public String getTitle() {
			return title;
		}
	}
}
