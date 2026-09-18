package com.taskflow.task.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Tasks", description = "Task content, placement and Board-scoped Search.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(ApiPaths.API)
public class TaskController {
	private final TaskService tasks;

	public TaskController(TaskService tasks) { this.tasks = tasks; }

	@Operation(operationId = "boardTaskList", summary = "List Board Tasks",
			description = "Returns all Tasks of the owned Board in canonical Column position, Task position, then Task ID ascending order. Missing and other-owner Boards both return BOARD_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@GetMapping("/boards/{boardId}/tasks")
	List<TaskResponse> listBoard(@PathVariable UUID boardId) {
		return tasks.listBoardTasks(boardId).stream().map(TaskResponse::from).toList();
	}

	@Operation(operationId = "taskSearch", summary = "Search Board Tasks",
			description = "Owner- and Board-scoped, case-insensitive literal substring matching on title OR description. Trims query edges; blank queries return an empty list. Maximum 200 characters after trimming. Results order by Column position, Task position, then Task ID ascending; no relevance ranking.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidSearchRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@GetMapping("/boards/{boardId}/tasks/search")
	List<TaskResponse> search(@PathVariable UUID boardId, @Parameter(description = "Literal substring, at most 200 characters after trimming; blank returns [].", example = "release notes") @RequestParam String q) {
		return tasks.searchTasks(boardId, q).stream().map(TaskResponse::from).toList();
	}

	@Operation(operationId = "taskList", summary = "List Column Tasks",
			description = "Returns canonical Task position order, then ID. Missing and other-owner Columns return COLUMN_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/ColumnNotFound")
	})
	@GetMapping("/columns/{columnId}/tasks")
	List<TaskResponse> list(@PathVariable UUID columnId) {
		return tasks.listTasks(columnId).stream().map(TaskResponse::from).toList();
	}

	@Operation(operationId = "taskCreate", summary = "Create a Task",
			description = "Appends to the owned Column. Missing or null priority defaults to MEDIUM.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/ColumnNotFound")
	})
	@PostMapping("/columns/{columnId}/tasks")
	ResponseEntity<TaskResponse> create(@PathVariable UUID columnId, @Valid @RequestBody CreateTaskRequest request) {
		var task = tasks.createTask(columnId, request.title(), request.description(), request.priority(), request.dueDate());
		return ResponseEntity.created(URI.create(ApiPaths.API + "/tasks/" + task.id())).body(TaskResponse.from(task));
	}

	@Operation(operationId = "taskGet", summary = "Get a Task",
			description = "Missing and other-owner Tasks both return TASK_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/TaskNotFound")
	})
	@GetMapping("/tasks/{taskId}")
	TaskResponse get(@PathVariable UUID taskId) { return TaskResponse.from(tasks.getTask(taskId)); }

	@Operation(operationId = "taskUpdate", summary = "Replace Task content",
			description = "Full content replacement: title and priority are required; omitted or null description and dueDate clear those values. Column and position are unchanged; use the placement operation to move a Task.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/TaskNotFound")
	})
	@PutMapping("/tasks/{taskId}")
	TaskResponse update(@PathVariable UUID taskId, @Valid @RequestBody UpdateTaskRequest request) {
		return TaskResponse.from(tasks.updateTask(taskId, request.title(), request.description(), request.priority(), request.dueDate()));
	}

	@Operation(operationId = "taskDelete", summary = "Delete a Task",
			description = "Deletes the owned Task and compacts remaining positions in its Column.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content", content = @Content),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/TaskNotFound")
	})
	@DeleteMapping("/tasks/{taskId}")
	ResponseEntity<Void> delete(@PathVariable UUID taskId) {
		tasks.deleteTask(taskId);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "taskPlace", summary = "Move a Task",
			description = "Changes Column and zero-based position within the same owned Board without replacing content. Position is evaluated after removing the Task from its source; inserting at the target size appends. Missing, inaccessible or other-Board target Columns return COLUMN_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/PlacementNotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/TaskPlacementConflict")
	})
	@PutMapping("/tasks/{taskId}/placement")
	TaskResponse place(@PathVariable UUID taskId, @Valid @RequestBody TaskPlacementRequest request) {
		return TaskResponse.from(tasks.placeTask(taskId, request.columnId(), request.position()));
	}
}
