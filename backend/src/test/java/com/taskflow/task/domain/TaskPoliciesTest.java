package com.taskflow.task.domain;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskPoliciesTest {
	@Test
	void titleStripsEdgesAndPreservesCaseAndInteriorWhitespace() {
		assertThat(TaskTitle.requireValid(" \u2003 Fix   Login bug \t")).isEqualTo("Fix   Login bug");
	}
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n", "\u2003"})
	void blankTitleRejected(String value) {
		assertThatIllegalArgumentException().isThrownBy(() -> TaskTitle.requireValid(value));
	}
	@Test
	void titleBoundaryAppliesAfterNormalization() {
		assertThat(TaskTitle.requireValid(" " + "x".repeat(200) + " ")).hasSize(200);
		assertThatIllegalArgumentException().isThrownBy(() -> TaskTitle.requireValid("x".repeat(201)));
	}
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n", "\u2003"})
	void emptyDescriptionNormalizesToNull(String value) {
		assertThat(TaskDescription.requireValid(value)).isNull();
	}
	@Test
	void descriptionPreservesInteriorNewlinesSpacingAndCase() {
		assertThat(TaskDescription.requireValid("  First  line\nSecond line  ")).isEqualTo("First  line\nSecond line");
	}
	@Test
	void descriptionBoundaryAppliesAfterNormalization() {
		assertThat(TaskDescription.requireValid(" " + "x".repeat(4000) + " ")).hasSize(4000);
		assertThatIllegalArgumentException().isThrownBy(() -> TaskDescription.requireValid("x".repeat(4001)));
	}
}
