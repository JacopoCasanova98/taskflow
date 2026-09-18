package com.taskflow.column.api.dto;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class ReorderColumnsRequestTest {
	@Test
	void ownsAnImmutableSnapshotOfTheSubmittedOrder() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var source = new ArrayList<>(List.of(first, second));
		var request = new ReorderColumnsRequest(source);
		source.clear();
		assertThat(request.columnIds()).containsExactly(first, second);
		assertThatThrownBy(() -> request.columnIds().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void preservesNullInputForBeanValidationInsteadOfThrowingDuringConstruction() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			assertThat(validator.validate(new ReorderColumnsRequest(null))).hasSize(1);
			assertThat(validator.validate(new ReorderColumnsRequest(Arrays.asList(UUID.randomUUID(), null))))
					.hasSize(1);
		}
	}
}
