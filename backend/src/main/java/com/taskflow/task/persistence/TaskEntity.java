package com.taskflow.task.persistence;

import java.time.LocalDate;
import java.util.Objects;
import com.taskflow.column.persistence.ColumnEntity;
import com.taskflow.shared.persistence.BaseEntity;
import com.taskflow.task.domain.TaskDescription;
import com.taskflow.task.domain.TaskPriority;
import com.taskflow.task.domain.TaskTitle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class TaskEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "column_id", nullable = false)
	private ColumnEntity column;

	@Column(nullable = false, length = TaskTitle.MAX_LENGTH)
	private String title;

	@Column(length = TaskDescription.MAX_LENGTH)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 6)
	private TaskPriority priority;

	@Column
	private LocalDate dueDate;

	@Column(nullable = false)
	private int position;

	protected TaskEntity() {
	}

	public TaskEntity(ColumnEntity column, String title, String description,
			TaskPriority priority, LocalDate dueDate, int position) {
		updateContent(title, description, priority == null ? TaskPriority.MEDIUM : priority, dueDate);
		place(column, position);
	}

	public void updateContent(String title, String description, TaskPriority priority, LocalDate dueDate) {
		String validTitle = TaskTitle.requireValid(title);
		String validDescription = TaskDescription.requireValid(description);
		Objects.requireNonNull(priority, "Task priority is required.");
		this.title = validTitle;
		this.description = validDescription;
		this.priority = priority;
		this.dueDate = dueDate;
	}

	/** Placement/resequencing only, within the owning Board's mutation lock and transaction. */
	public void place(ColumnEntity column, int position) {
		Objects.requireNonNull(column, "Task column is required.");
		if (position < 0) {
			throw new IllegalArgumentException("Task position must be non-negative.");
		}
		this.column = column;
		this.position = position;
	}

	public ColumnEntity getColumn() {
		return column;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public TaskPriority getPriority() {
		return priority;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public int getPosition() {
		return position;
	}
}
