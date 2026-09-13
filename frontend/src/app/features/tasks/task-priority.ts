import { sortTasks, TaskOrder } from './task-order';
export type { TaskOrder } from './task-order';
import { Task, TaskPriority } from './task.models';

export const priorityLabels: Readonly<Record<TaskPriority, string>> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
};
export const taskPriorities: readonly TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
export type PriorityFilter = 'ALL' | TaskPriority;

/** Temporary presentation only: never change canonical arrays, positions, or Task objects. */
export function projectTasksByPriority(
  tasks: readonly Task[],
  filter: PriorityFilter,
  order: TaskOrder,
): readonly Task[] {
  const visible = tasks.filter((task) => filter === 'ALL' || task.priority === filter);
  return sortTasks(visible, order);
}
