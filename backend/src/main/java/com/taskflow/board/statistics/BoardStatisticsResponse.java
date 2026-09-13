package com.taskflow.board.statistics;

import java.util.List;
import java.util.UUID;

public record BoardStatisticsResponse(long totalTasks, long overdueTasks,
		PriorityDistribution priorityDistribution, List<StatusCount> statusDistribution) {
	public record PriorityDistribution(long low, long medium, long high) {}
	public record StatusCount(UUID columnId, String name, int position, long taskCount) {}
}
