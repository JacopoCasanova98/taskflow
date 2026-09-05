package com.taskflow.shared.error;

/**
 * A single invalid input field exposed as an extension of a Problem Detail response.
 */
public record FieldViolation(String field, String message, String code) {
}
