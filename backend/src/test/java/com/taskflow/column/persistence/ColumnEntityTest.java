package com.taskflow.column.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.Arrays;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnEntityTest {
	private final BoardEntity board = new BoardEntity(UUID.randomUUID(), "Board");

	@Test
	void retainsBoardAndNormalizesNameWithoutChangingCaseOrInteriorWhitespace() {
		var column = new ColumnEntity(board, " \t\u2003In   Progress\u2003\n ", 2);
		assertThat(column.getName()).isEqualTo("In   Progress");
		assertThat(column.getBoard()).isSameAs(board);
		column.rename("  Next   Step  ");
		assertThat(column.getName()).isEqualTo("Next   Step");
		assertThat(column.getPosition()).isEqualTo(2);
		assertThat(column.getBoard()).isSameAs(board);
	}

	@Test
	void requiresBoardAndExposesNoParentReplacementOrGenericSetters() {
		assertThatNullPointerException().isThrownBy(() -> new ColumnEntity(null, "Name", 0));
		assertThat(Arrays.stream(ColumnEntity.class.getMethods()).map(java.lang.reflect.Method::getName))
				.doesNotContain("setBoard", "setPosition", "setName", "setOwnerId");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n\r", "\u2003"})
	void rejectsInvalidNamesOnCreationAndRename(String name) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ColumnEntity(board, name, 0));
		var column = new ColumnEntity(board, "Original", 0);
		assertThatIllegalArgumentException().isThrownBy(() -> column.rename(name));
		assertThat(column.getName()).isEqualTo("Original");
	}

	@Test
	void lengthAppliesAfterNormalization() {
		var column = new ColumnEntity(board, "  " + "x".repeat(120) + "  ", 0);
		assertThat(column.getName()).hasSize(120);
		column.rename("  " + "y".repeat(120) + "  ");
		assertThat(column.getName()).isEqualTo("y".repeat(120));
		assertThatIllegalArgumentException().isThrownBy(() -> new ColumnEntity(board, "x".repeat(121), 0));
		assertThatIllegalArgumentException().isThrownBy(() -> column.rename("x".repeat(121)));
		assertThat(column.getName()).isEqualTo("y".repeat(120));
	}

	@Test
	void positionAssignmentRejectsNegativeValuesAndRetainsOtherState() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ColumnEntity(board, "Name", -1));
		var column = new ColumnEntity(board, "Name", 0);
		column.assignPosition(3);
		assertThat(column.getPosition()).isEqualTo(3);
		assertThatIllegalArgumentException().isThrownBy(() -> column.assignPosition(-1));
		assertThat(column.getPosition()).isEqualTo(3);
		assertThat(column.getName()).isEqualTo("Name");
		assertThat(column.getBoard()).isSameAs(board);
	}
}
