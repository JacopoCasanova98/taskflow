package com.taskflow.task.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.taskflow.task.api.dto.CreateTaskRequest;
import com.taskflow.task.api.dto.TaskPlacementRequest;
import com.taskflow.task.api.dto.TaskResponse;
import com.taskflow.task.api.dto.UpdateTaskRequest;
import com.taskflow.task.application.TaskService;
import com.taskflow.shared.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.API)
public class TaskController {
	private final TaskService tasks;

	public TaskController(TaskService tasks) { this.tasks = tasks; }

	@GetMapping("/columns/{columnId}/tasks")
	List<TaskResponse> list(@PathVariable UUID columnId) {
		return tasks.listTasks(columnId).stream().map(TaskResponse::from).toList();
	}

	@PostMapping("/columns/{columnId}/tasks")
	ResponseEntity<TaskResponse> create(@PathVariable UUID columnId, @Valid @RequestBody CreateTaskRequest request) {
		var task = tasks.createTask(columnId, request.title(), request.description(), request.priority(), request.dueDate());
		return ResponseEntity.created(URI.create(ApiPaths.API + "/tasks/" + task.id())).body(TaskResponse.from(task));
	}

	@GetMapping("/tasks/{taskId}")
	TaskResponse get(@PathVariable UUID taskId) { return TaskResponse.from(tasks.getTask(taskId)); }

	@PutMapping("/tasks/{taskId}")
	TaskResponse update(@PathVariable UUID taskId, @Valid @RequestBody UpdateTaskRequest request) {
		return TaskResponse.from(tasks.updateTask(taskId, request.title(), request.description(), request.priority(), request.dueDate()));
	}

	@DeleteMapping("/tasks/{taskId}")
	ResponseEntity<Void> delete(@PathVariable UUID taskId) {
		tasks.deleteTask(taskId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/tasks/{taskId}/placement")
	TaskResponse place(@PathVariable UUID taskId, @Valid @RequestBody TaskPlacementRequest request) {
		return TaskResponse.from(tasks.placeTask(taskId, request.columnId(), request.position()));
	}
}
