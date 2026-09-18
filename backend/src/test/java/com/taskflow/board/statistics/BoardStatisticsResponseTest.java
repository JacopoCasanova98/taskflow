package com.taskflow.board.statistics;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardStatisticsResponseTest {
	@Test
	void ownsAnImmutableSnapshotOfStatusCounts() {
		var count = new BoardStatisticsResponse.StatusCount(UUID.randomUUID(), "Todo", 0, 2);
		var source = new ArrayList<>(List.of(count));
		var response = new BoardStatisticsResponse(2, 0,
				new BoardStatisticsResponse.PriorityDistribution(0, 2, 0), source);
		source.clear();
		assertThat(response.statusDistribution()).containsExactly(count);
		assertThatThrownBy(() -> response.statusDistribution().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
