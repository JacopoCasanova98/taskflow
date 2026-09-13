import { DueDateFilter, filterTasksByDueDate } from './task-due-date';
import { Task } from './task.models';
import { PriorityFilter, projectTasksByPriority, TaskOrder } from './task-priority';

export interface TaskProjection {
  readonly searchActive: boolean;
  /** Null means no successful Search result yet; retain canonical membership. */
  readonly searchMembership: ReadonlySet<string> | null;
  readonly columnId: string;
  readonly priority: PriorityFilter;
  readonly dueDate: DueDateFilter;
  readonly order: TaskOrder;
  readonly today: string;
}

/** Explicit AND pipeline, followed by existing ordering. Never modifies canonical Tasks. */
export function projectTasks(tasks: readonly Task[], view: TaskProjection): readonly Task[] {
  const searched = tasks.filter(
    (task) =>
      !view.searchActive || view.searchMembership === null || view.searchMembership.has(task.id),
  );
  const inColumn = searched.filter(
    (task) => view.columnId === 'ALL' || task.columnId === view.columnId,
  );
  const priority = projectTasksByPriority(inColumn, view.priority, 'MANUAL');
  const due = filterTasksByDueDate(priority, view.dueDate, view.today);
  return projectTasksByPriority(due, 'ALL', view.order);
}
