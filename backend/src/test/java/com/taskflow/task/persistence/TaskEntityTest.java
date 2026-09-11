package com.taskflow.task.persistence;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.UUID;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.task.domain.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskEntityTest {
	private final BoardEntity board = new BoardEntity(UUID.randomUUID(), "Board");
	private final ColumnEntity column = new ColumnEntity(board, "Column", 0);
	private final LocalDate date = LocalDate.of(2026, 9, 15);

	@Test
	void creationRequiresColumnAndNonNegativePosition() {
		assertThatNullPointerException().isThrownBy(() -> new TaskEntity(null, "Title", null, null, null, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new TaskEntity(column, "Title", null, null, null, -1));
	}
	@Test
	void defaultPriorityAndNullableContent() {
		var task = new TaskEntity(column, "  Title  ", "  ", null, null, 0);
		assertThat(task.getColumn()).isSameAs(column);
		assertThat(task.getTitle()).isEqualTo("Title");
		assertThat(task.getDescription()).isNull();
		assertThat(task.getDueDate()).isNull();
		assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(task.getPosition()).isZero();
	}
	@ParameterizedTest
	@EnumSource(TaskPriority.class)
	void explicitPriorityAndDateRetained(TaskPriority priority) {
		var task = new TaskEntity(column, "Title", "Description", priority, date, 2);
		assertThat(task.getPriority()).isEqualTo(priority);
		assertThat(task.getDueDate()).isEqualTo(date);
	}
	@Test
	void fullContentUpdateRetainsPlacementAndCanClearNullableFields() {
		var task = new TaskEntity(column, "Title", "Description", TaskPriority.LOW, date, 2);
		task.updateContent(" Next ", " Changed ", TaskPriority.HIGH, date.plusDays(1));
		assertThat(task.getTitle()).isEqualTo("Next");
		assertThat(task.getDescription()).isEqualTo("Changed");
		assertThat(task.getDueDate()).isEqualTo(date.plusDays(1));
		task.updateContent("Next", null, TaskPriority.MEDIUM, null);
		assertThat(task.getDescription()).isNull();
		assertThat(task.getDueDate()).isNull();
		assertThat(task.getColumn()).isSameAs(column);
		assertThat(task.getPosition()).isEqualTo(2);
	}
	@Test
	void invalidContentUpdateDoesNotPartiallyChangeEntity() {
		var task = new TaskEntity(column, "Title", "Description", TaskPriority.LOW, date, 0);
		assertThatNullPointerException().isThrownBy(() -> task.updateContent("Next", null, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> task.updateContent("Next", "x".repeat(4001), TaskPriority.HIGH, null));
		assertThatIllegalArgumentException().isThrownBy(() -> task.updateContent(" ", null, TaskPriority.HIGH, null));
		assertThat(task.getTitle()).isEqualTo("Title");
		assertThat(task.getDescription()).isEqualTo("Description");
		assertThat(task.getPriority()).isEqualTo(TaskPriority.LOW);
		assertThat(task.getDueDate()).isEqualTo(date);
	}
	@Test
	void placementChangesOnlyColumnAndPosition() {
		var task = new TaskEntity(column, "Title", "Description", TaskPriority.HIGH, date, 0);
		var target = new ColumnEntity(board, "Target", 1);
		task.place(target, 3);
		assertThat(task.getColumn()).isSameAs(target);
		assertThat(task.getPosition()).isEqualTo(3);
		assertThat(task.getTitle()).isEqualTo("Title");
		assertThat(task.getDescription()).isEqualTo("Description");
		assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
		assertThat(task.getDueDate()).isEqualTo(date);
		assertThatIllegalArgumentException().isThrownBy(() -> task.place(column, -1));
		assertThatNullPointerException().isThrownBy(() -> task.place(null, 0));
		assertThat(task.getColumn()).isSameAs(target);
		assertThat(task.getPosition()).isEqualTo(3);
	}
}
