export interface PriorityDistribution {
  readonly low: number;
  readonly medium: number;
  readonly high: number;
}
export interface StatusCount {
  readonly columnId: string;
  readonly name: string;
  readonly position: number;
  readonly taskCount: number;
}
export interface BoardStatistics {
  readonly totalTasks: number;
  readonly overdueTasks: number;
  readonly priorityDistribution: PriorityDistribution;
  readonly statusDistribution: readonly StatusCount[];
}
