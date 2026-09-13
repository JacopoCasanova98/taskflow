package com.taskflow.column.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import java.util.List;
import java.util.UUID;
import com.taskflow.column.api.dto.ColumnResponse;
import com.taskflow.column.api.dto.CreateColumnRequest;
import com.taskflow.column.api.dto.RenameColumnRequest;
import com.taskflow.column.api.dto.ReorderColumnsRequest;
import com.taskflow.column.application.ColumnService;
import com.taskflow.shared.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Columns", description = "Board workflow Columns and their canonical order.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(ApiPaths.API)
public class ColumnController {

	private final ColumnService columns;

	public ColumnController(ColumnService columns) {
		this.columns = columns;
	}

	@Operation(operationId = "columnList", summary = "List Board Columns",
			description = "Returns canonical position order, then ID. Missing and other-owner Boards return BOARD_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@GetMapping("/boards/{boardId}/columns")
	List<ColumnResponse> list(@PathVariable UUID boardId) {
		return columns.listColumns(boardId).stream().map(ColumnResponse::from).toList();
	}

	@Operation(operationId = "columnCreate", summary = "Create a Column",
			description = "Appends a Column to the owned Board.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@PostMapping("/boards/{boardId}/columns")
	ResponseEntity<ColumnResponse> create(@PathVariable UUID boardId, @Valid @RequestBody CreateColumnRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ColumnResponse.from(columns.createColumn(boardId, request.name())));
	}

	@Operation(operationId = "columnRename", summary = "Rename a Column",
			description = "Missing and other-owner Columns both return COLUMN_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/ColumnNotFound")
	})
	@PatchMapping("/columns/{columnId}")
	ColumnResponse rename(@PathVariable UUID columnId, @Valid @RequestBody RenameColumnRequest request) {
		return ColumnResponse.from(columns.renameColumn(columnId, request.name()));
	}

	@Operation(operationId = "columnDelete", summary = "Delete an empty Column",
			description = "Requires an empty Column; compacts remaining Column positions.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content", content = @Content),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/ColumnNotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/ColumnNotEmpty")
	})
	@DeleteMapping("/columns/{columnId}")
	ResponseEntity<Void> delete(@PathVariable UUID columnId) {
		columns.deleteColumn(columnId);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "columnReorder", summary = "Reorder Board Columns",
			description = "Supply every current Column ID exactly once in the desired order. Returns canonical zero-based positions. A stale, duplicate or foreign membership produces COLUMN_ORDER_CONFLICT.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/ColumnOrderConflict")
	})
	@PutMapping("/boards/{boardId}/columns/order")
	List<ColumnResponse> reorder(@PathVariable UUID boardId, @Valid @RequestBody ReorderColumnsRequest request) {
		return columns.reorderColumns(boardId, request.columnIds()).stream().map(ColumnResponse::from).toList();
	}
}
