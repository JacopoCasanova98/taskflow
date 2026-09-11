package com.taskflow.column.domain;

/** Shared name policy for request normalization and persistence invariants. */
public final class ColumnName {

	public static final int MAX_LENGTH = 120;

	private ColumnName() {
	}

	public static String normalize(String name) {
		return name == null ? null : name.strip();
	}

	public static String requireValid(String name) {
		String normalized = normalize(name);
		if (normalized == null || normalized.isBlank() || normalized.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Column name must be non-blank and at most 120 characters.");
		}
		return normalized;
	}
}
