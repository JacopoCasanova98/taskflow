package com.taskflow.task.application;

import java.util.UUID;
import com.taskflow.column.application.ColumnTaskPresence;
import com.taskflow.task.persistence.TaskRepository;
import org.springframework.stereotype.Component;

@Component
public class TaskColumnPresence implements ColumnTaskPresence {
	private final TaskRepository tasks;

	public TaskColumnPresence(TaskRepository tasks) { this.tasks = tasks; }

	@Override
	public boolean hasTasks(UUID columnId) { return tasks.existsByColumn_Id(columnId); }
}
