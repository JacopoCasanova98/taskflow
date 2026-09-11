package com.taskflow.column.api;

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

@RestController
@RequestMapping(ApiPaths.API)
public class ColumnController {

	private final ColumnService columns;

	public ColumnController(ColumnService columns) {
		this.columns = columns;
	}

	@GetMapping("/boards/{boardId}/columns")
	List<ColumnResponse> list(@PathVariable UUID boardId) {
		return columns.listColumns(boardId).stream().map(ColumnResponse::from).toList();
	}

	@PostMapping("/boards/{boardId}/columns")
	ResponseEntity<ColumnResponse> create(@PathVariable UUID boardId, @Valid @RequestBody CreateColumnRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ColumnResponse.from(columns.createColumn(boardId, request.name())));
	}

	@PatchMapping("/columns/{columnId}")
	ColumnResponse rename(@PathVariable UUID columnId, @Valid @RequestBody RenameColumnRequest request) {
		return ColumnResponse.from(columns.renameColumn(columnId, request.name()));
	}

	@DeleteMapping("/columns/{columnId}")
	ResponseEntity<Void> delete(@PathVariable UUID columnId) {
		columns.deleteColumn(columnId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/boards/{boardId}/columns/order")
	List<ColumnResponse> reorder(@PathVariable UUID boardId, @Valid @RequestBody ReorderColumnsRequest request) {
		return columns.reorderColumns(boardId, request.columnIds()).stream().map(ColumnResponse::from).toList();
	}
}
