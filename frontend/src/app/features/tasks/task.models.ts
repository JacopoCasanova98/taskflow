export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export interface CreateTaskRequest {
  title: string;
  description: string | null;
  priority?: TaskPriority;
  dueDate: string | null;
}
export interface UpdateTaskRequest {
  title: string;
  description: string | null;
  priority: TaskPriority;
  dueDate: string | null;
}

export interface Task {
  readonly id: string;
  readonly columnId: string;
  readonly title: string;
  readonly description: string | null;
  readonly priority: TaskPriority;
  readonly dueDate: string | null;
  readonly position: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface TaskPlacementRequest {
  columnId: string;
  position: number;
}
