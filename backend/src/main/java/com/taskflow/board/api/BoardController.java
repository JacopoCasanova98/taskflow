package com.taskflow.board.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.taskflow.board.api.dto.BoardResponse;
import com.taskflow.board.api.dto.CreateBoardRequest;
import com.taskflow.board.api.dto.RenameBoardRequest;
import com.taskflow.board.application.BoardService;
import com.taskflow.shared.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Boards", description = "Private Boards owned by the current user.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(ApiPaths.API + "/boards")
public class BoardController {

	private final BoardService boards;

	public BoardController(BoardService boards) {
		this.boards = boards;
	}

	@Operation(operationId = "boardList", summary = "List owned Boards",
			description = "Boards ordered by creation time and ID, descending.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true)
	})
	@GetMapping
	List<BoardResponse> list() {
		return boards.listBoards().stream().map(BoardResponse::from).toList();
	}

	@Operation(operationId = "boardCreate", summary = "Create a Board",
			description = "Creates a Board owned by the authenticated user.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest")
	})
	@PostMapping
	ResponseEntity<BoardResponse> create(@Valid @RequestBody CreateBoardRequest request) {
		var board = BoardResponse.from(boards.createBoard(request.name()));
		return ResponseEntity.created(URI.create(ApiPaths.API + "/boards/" + board.id())).body(board);
	}

	@Operation(operationId = "boardGet", summary = "Get a Board",
			description = "Missing and other-owner Boards both return BOARD_NOT_FOUND.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@GetMapping("/{boardId}")
	BoardResponse get(@PathVariable UUID boardId) {
		return BoardResponse.from(boards.getBoard(boardId));
	}

	@Operation(operationId = "boardRename", summary = "Rename a Board",
			description = "Changes the name of an owned Board.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@PatchMapping("/{boardId}")
	BoardResponse rename(@PathVariable UUID boardId, @Valid @RequestBody RenameBoardRequest request) {
		return BoardResponse.from(boards.renameBoard(boardId, request.name()));
	}

	@Operation(operationId = "boardDelete", summary = "Delete a Board",
			description = "Deletes the owned Board and its Columns and Tasks.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content", content = @Content),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@DeleteMapping("/{boardId}")
	ResponseEntity<Void> delete(@PathVariable UUID boardId) {
		boards.deleteBoard(boardId);
		return ResponseEntity.noContent().build();
	}
}
