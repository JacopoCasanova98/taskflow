package com.taskflow.shared.error;

import static com.taskflow.shared.error.ProblemDetails.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Optional;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
		return problem(
				exception.getStatus(),
				exception.getTitle(),
				exception.getMessage(),
				exception.getCode(),
				request
		);
	}

	@ExceptionHandler(BindException.class)
	ProblemDetail handleBindException(BindException exception, HttpServletRequest request) {
		List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.toList();

		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				"One or more request fields are invalid.",
				"VALIDATION_FAILED",
				request
		);
		problem.setProperty("violations", violations);
		return problem;
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail handleHandlerMethodValidationException(
			HandlerMethodValidationException exception,
			HttpServletRequest request
	) {
		if (exception.isForReturnValue()) {
			return handleUnexpectedException(exception, request);
		}

		List<FieldViolation> violations = exception.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldViolation(
								Optional.ofNullable(result.getMethodParameter().getParameterName()).orElse("request"),
								defaultMessage(error),
								errorCode(error)
						)))
				.toList();

		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				"One or more request values are invalid.",
				"VALIDATION_FAILED",
				request
		);
		problem.setProperty("violations", violations);
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolationException(
			ConstraintViolationException exception,
			HttpServletRequest request
	) {
		List<FieldViolation> violations = exception.getConstraintViolations().stream()
				.map(violation -> new FieldViolation(
						violation.getPropertyPath().toString(),
						violation.getMessage(),
						violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
				))
				.toList();

		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				"One or more request values are invalid.",
				"VALIDATION_FAILED",
				request
		);
		problem.setProperty("violations", violations);
		return problem;
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
			TypeMismatchException.class})
	ProblemDetail handleMalformedRequest(Exception exception, HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"Malformed request",
				"The request body or parameters could not be read.",
				"MALFORMED_REQUEST",
				request
		);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ProblemDetail handleMethodNotSupported(HttpServletRequest request) {
		return problem(
				HttpStatus.METHOD_NOT_ALLOWED,
				"Method not allowed",
				"The HTTP method is not supported for this resource.",
				"METHOD_NOT_ALLOWED",
				request
		);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResourceFound(HttpServletRequest request) {
		return problem(
				HttpStatus.NOT_FOUND,
				"Resource not found",
				"The requested resource does not exist.",
				"RESOURCE_NOT_FOUND",
				request
		);
	}

	@ExceptionHandler(ErrorResponseException.class)
	ProblemDetail handleFrameworkError(ErrorResponseException exception, HttpServletRequest request) {
		return problem(
				exception.getStatusCode(),
				"Request could not be completed",
				"The request could not be processed.",
				"REQUEST_FAILED",
				request
		);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
		if (exception instanceof ErrorResponse error && !error.getStatusCode().is5xxServerError()) {
			return problem(error.getStatusCode(), "Request could not be completed",
					"The request could not be processed.", "REQUEST_FAILED", request);
		}
		LOGGER.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Internal server error",
				"An unexpected error occurred.",
				"INTERNAL_ERROR",
				request
		);
	}

	private FieldViolation toFieldViolation(FieldError error) {
		return new FieldViolation(error.getField(),
				error.isBindingFailure() ? "Invalid value." : defaultMessage(error), errorCode(error));
	}

	private String defaultMessage(MessageSourceResolvable error) {
		return Optional.ofNullable(error.getDefaultMessage()).orElse("Invalid value.");
	}

	private String errorCode(MessageSourceResolvable error) {
		return Optional.ofNullable(error.getCodes())
				.filter(codes -> codes.length > 0)
				.map(codes -> codes[codes.length - 1])
				.orElse("INVALID");
	}

}
