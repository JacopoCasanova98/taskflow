package com.taskflow.task.domain;

public final class TaskDescription {
	public static final int MAX_LENGTH = 4000;

	private TaskDescription() {
	}

	public static String normalize(String value) {
		String normalized = value == null ? null : value.strip();
		return normalized == null || normalized.isBlank() ? null : normalized;
	}

	public static String requireValid(String value) {
		String normalized = normalize(value);
		if (normalized != null && normalized.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Task description must be at most 4000 characters.");
		}
		return normalized;
	}
}
