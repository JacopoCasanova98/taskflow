package com.taskflow.board.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import com.taskflow.shared.web.ApiPaths;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Board Statistics", description = "Canonical Board counts using an explicit civil date.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(ApiPaths.API + "/boards/{boardId}/statistics")
public class BoardStatisticsController {
	private final BoardStatisticsService statistics;

	public BoardStatisticsController(BoardStatisticsService statistics) {
		this.statistics = statistics;
	}

	// ISO.DATE also accepts offsets. This endpoint requires an exact civil date.
	@InitBinder("asOf")
	void bindAsOf(WebDataBinder binder) {
		binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
					throw new IllegalArgumentException("Expected YYYY-MM-DD");
				try {
					setValue(LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE));
				} catch (DateTimeParseException exception) {
					throw new IllegalArgumentException("Expected a valid calendar date", exception);
				}
			}
		});
	}

	@Operation(operationId = "boardStatisticsGet", summary = "Get Board statistics",
			description = "Describes canonical Board data, independent of frontend filters. Overdue means dueDate < asOf; due today is not overdue. Status distribution follows Columns, including empty Columns. Completed/open metrics are intentionally absent because no completion semantic exists.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/MalformedRequest"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/BoardNotFound")
	})
	@GetMapping
	BoardStatisticsResponse get(@PathVariable UUID boardId,
			@Parameter(description = "Required civil date, exactly YYYY-MM-DD.", example = "2026-09-13",
					schema = @Schema(type = "string", format = "date")) @RequestParam LocalDate asOf) {
		return statistics.getBoardStatistics(boardId, asOf);
	}
}
