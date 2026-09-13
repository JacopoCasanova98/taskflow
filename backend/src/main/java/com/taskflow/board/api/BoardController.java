package com.taskflow.board.api;

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

@RestController
@RequestMapping(ApiPaths.API + "/boards")
public class BoardController {

	private final BoardService boards;

	public BoardController(BoardService boards) {
		this.boards = boards;
	}

	@GetMapping
	List<BoardResponse> list() {
		return boards.listBoards().stream().map(BoardResponse::from).toList();
	}

	@PostMapping
	ResponseEntity<BoardResponse> create(@Valid @RequestBody CreateBoardRequest request) {
		var board = BoardResponse.from(boards.createBoard(request.name()));
		return ResponseEntity.created(URI.create(ApiPaths.API + "/boards/" + board.id())).body(board);
	}

	@GetMapping("/{boardId}")
	BoardResponse get(@PathVariable UUID boardId) {
		return BoardResponse.from(boards.getBoard(boardId));
	}

	@PatchMapping("/{boardId}")
	BoardResponse rename(@PathVariable UUID boardId, @Valid @RequestBody RenameBoardRequest request) {
		return BoardResponse.from(boards.renameBoard(boardId, request.name()));
	}

	@DeleteMapping("/{boardId}")
	ResponseEntity<Void> delete(@PathVariable UUID boardId) {
		boards.deleteBoard(boardId);
		return ResponseEntity.noContent().build();
	}
}
