package com.taskflow.task.persistence;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import com.taskflow.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** Static mapping/query contracts; no actual PostgreSQL persistence or concurrency is exercised. */
class TaskPersistenceContractTest {
	@Test
	void taskExtendsBaseEntityAndHasRequiredMutableLazyParentOnly() throws Exception {
		assertThat(TaskEntity.class.getSuperclass()).isEqualTo(BaseEntity.class);
		assertThat(TaskEntity.class.getAnnotation(Table.class).name()).isEqualTo("tasks");
		var field = TaskEntity.class.getDeclaredField("column");
		assertThat(field.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
		assertThat(field.getAnnotation(ManyToOne.class).optional()).isFalse();
		assertThat(field.getAnnotation(JoinColumn.class).name()).isEqualTo("column_id");
		assertThat(field.getAnnotation(JoinColumn.class).nullable()).isFalse();
		assertThat(field.getAnnotation(JoinColumn.class).updatable()).isTrue();
		assertThat(TaskEntity.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
				.containsExactlyInAnyOrder("column", "title", "description", "priority", "dueDate", "position");
	}
	@Test
	void contentMappingMatchesMigrationTypesAndBounds() throws Exception {
		var title = TaskEntity.class.getDeclaredField("title").getAnnotation(Column.class);
		assertThat(title.length()).isEqualTo(200); assertThat(title.nullable()).isFalse();
		var description = TaskEntity.class.getDeclaredField("description").getAnnotation(Column.class);
		assertThat(description.length()).isEqualTo(4000); assertThat(description.nullable()).isTrue();
		var priority = TaskEntity.class.getDeclaredField("priority");
		assertThat(priority.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
		assertThat(priority.getAnnotation(Column.class).nullable()).isFalse();
		assertThat(priority.getAnnotation(Column.class).length()).isEqualTo(6);
		assertThat(TaskEntity.class.getDeclaredField("dueDate").getType()).isEqualTo(LocalDate.class);
		assertThat(TaskEntity.class.getDeclaredField("dueDate").getAnnotation(Column.class).nullable()).isTrue();
	}
	@Test
	void discoveryIsScalarAndOwnershipScopedThroughColumnAndBoard() throws Exception {
		var method = TaskRepository.class.getMethod("findBoardIdByIdAndOwnerId", UUID.class, UUID.class);
		assertThat(method.getAnnotation(Query.class).value()).contains("select t.column.board.id", "t.id = :id", "t.column.board.ownerId = :ownerId");
	}
	@Test
	void v6DeclaresCascadeDeferredUniquenessAndOrderedIndexWithoutDefaults() throws Exception {
		try (var stream = getClass().getResourceAsStream("/db/migration/V6__create_tasks.sql")) {
			assertThat(stream).isNotNull();
			String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(sql).contains("CREATE TABLE tasks", "id UUID PRIMARY KEY", "column_id UUID NOT NULL",
					"REFERENCES columns(id) ON DELETE CASCADE", "title VARCHAR(200) NOT NULL", "description VARCHAR(4000)",
					"priority VARCHAR(6) NOT NULL", "due_date DATE", "position INTEGER NOT NULL", "CHECK (position >= 0)",
					"CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))", "CHECK (title ~ '[^[:space:]]')",
					"UNIQUE (column_id, position) DEFERRABLE INITIALLY DEFERRED", "ON tasks (column_id, position, id)",
					"created_at TIMESTAMP WITH TIME ZONE NOT NULL", "updated_at TIMESTAMP WITH TIME ZONE NOT NULL")
					.doesNotContain("DEFAULT", "owner_id", "board_id");
		}
	}
}
