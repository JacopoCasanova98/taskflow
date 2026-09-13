package com.taskflow.board.statistics;

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

	@GetMapping
	BoardStatisticsResponse get(@PathVariable UUID boardId,
			@RequestParam LocalDate asOf) {
		return statistics.getBoardStatistics(boardId, asOf);
	}
}
