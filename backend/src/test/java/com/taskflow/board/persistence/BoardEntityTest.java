package com.taskflow.board.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BoardEntityTest {

	private static final UUID OWNER = UUID.fromString("7b361e53-9880-477a-9ea1-eac3d57b138b");

	@Test
	void retainsOwnerAndStripsEdgesWhilePreservingInteriorWhitespace() {
		var board = new BoardEntity(OWNER, " \t\u2003Product   Roadmap\u2003\n ");
		assertThat(board.getOwnerId()).isEqualTo(OWNER);
		assertThat(board.getName()).isEqualTo("Product   Roadmap");
		board.rename("  Next  Roadmap  ");
		assertThat(board.getName()).isEqualTo("Next  Roadmap");
		assertThat(board.getOwnerId()).isEqualTo(OWNER);
	}

	@Test
	void requiresAnOwner() {
		assertThatNullPointerException().isThrownBy(() -> new BoardEntity(null, "Roadmap"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n\r", "\u2003"})
	void rejectsMissingOrBlankNamesOnCreationAndRename(String name) {
		assertThatIllegalArgumentException().isThrownBy(() -> new BoardEntity(OWNER, name));
		var board = new BoardEntity(OWNER, "Original");
		assertThatIllegalArgumentException().isThrownBy(() -> board.rename(name));
		assertThat(board.getName()).isEqualTo("Original");
	}

	@Test
	void enforcesLengthAfterNormalizationOnCreationAndRename() {
		String maximum = "x".repeat(120);
		var board = new BoardEntity(OWNER, "  " + maximum + "  ");
		assertThat(board.getName()).isEqualTo(maximum);
		board.rename("  " + "y".repeat(120) + "  ");
		assertThat(board.getName()).isEqualTo("y".repeat(120));
		assertThatIllegalArgumentException().isThrownBy(() -> new BoardEntity(OWNER, maximum + "x"));
		assertThatIllegalArgumentException().isThrownBy(() -> board.rename(maximum + "x"));
		assertThat(board.getName()).isEqualTo("y".repeat(120));
	}
}
