package com.taskflow.shared.error;

/**
 * Client-safe validation error for a request field.
 */
public record ApiFieldError(String field, String message) {
}
