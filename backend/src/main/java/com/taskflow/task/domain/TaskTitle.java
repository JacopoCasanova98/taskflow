package com.taskflow.task.domain;

public final class TaskTitle {
	public static final int MAX_LENGTH = 200;

	private TaskTitle() {
	}

	public static String normalize(String value) {
		return value == null ? null : value.strip();
	}

	public static String requireValid(String value) {
		String normalized = normalize(value);
		if (normalized == null || normalized.isBlank() || normalized.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Task title must be non-blank and at most 200 characters.");
		}
		return normalized;
	}
}
