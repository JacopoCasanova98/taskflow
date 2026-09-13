package com.taskflow.task.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TaskPriority {
	LOW, MEDIUM, HIGH;

	/** API values are enum names; numeric ordinal inputs are not part of the contract. */
	@JsonCreator
	public static TaskPriority fromValue(String value) {
		return value == null ? null : TaskPriority.valueOf(value);
	}
}
