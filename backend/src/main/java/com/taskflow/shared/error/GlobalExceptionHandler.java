package com.taskflow.shared.error;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
				.toList();

		return response(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"Request validation failed.",
				request,
				fieldErrors
		);
	}

	@ExceptionHandler({BadRequestException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is invalid.", request, List.of());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
			ResourceNotFoundException exception,
			HttpServletRequest request
	) {
		return response(
				HttpStatus.NOT_FOUND,
				"RESOURCE_NOT_FOUND",
				"The requested resource was not found.",
				request,
				List.of()
		);
	}

	@ExceptionHandler(ResourceConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceConflict(
			ResourceConflictException exception,
			HttpServletRequest request
	) {
		return response(
				HttpStatus.CONFLICT,
				"RESOURCE_CONFLICT",
				"The request conflicts with the current resource state.",
				request,
				List.of()
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		LOGGER.error("Unhandled exception for request path {}", request.getRequestURI(), exception);

		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_SERVER_ERROR",
				"An unexpected error occurred.",
				request,
				List.of()
		);
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status,
			String errorCode,
			String message,
			HttpServletRequest request,
			List<ApiFieldError> fieldErrors
	) {
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(),
				status.value(),
				errorCode,
				message,
				request.getRequestURI(),
				fieldErrors
		);

		return ResponseEntity.status(status).body(body);
	}
}
