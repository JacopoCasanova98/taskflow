import { Task, TaskPriority } from './task.models';

export const priorityLabels: Readonly<Record<TaskPriority, string>> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
};
export const taskPriorities: readonly TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
export type PriorityFilter = 'ALL' | TaskPriority;
export type TaskOrder = 'MANUAL' | 'PRIORITY_HIGH_TO_LOW' | 'PRIORITY_LOW_TO_HIGH';
const priorityRank: Readonly<Record<TaskPriority, number>> = { LOW: 1, MEDIUM: 2, HIGH: 3 };

/** Temporary presentation only: never change canonical arrays, positions, or Task objects. */
export function projectTasksByPriority(
  tasks: readonly Task[],
  filter: PriorityFilter,
  order: TaskOrder,
): readonly Task[] {
  const visible = tasks.filter((task) => filter === 'ALL' || task.priority === filter);
  if (order === 'MANUAL') return visible;
  const direction = order === 'PRIORITY_HIGH_TO_LOW' ? -1 : 1;
  return visible.sort(
    (a, b) =>
      direction * (priorityRank[a.priority] - priorityRank[b.priority]) || a.position - b.position,
  );
}
