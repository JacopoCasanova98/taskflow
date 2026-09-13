import { Task, TaskPriority } from './task.models';

export const taskOrders = [
  'MANUAL',
  'PRIORITY_HIGH_TO_LOW',
  'PRIORITY_LOW_TO_HIGH',
  'DUE_DATE_ASC',
  'DUE_DATE_DESC',
  'CREATED_NEWEST',
  'CREATED_OLDEST',
  'UPDATED_NEWEST',
  'UPDATED_OLDEST',
] as const;
export type TaskOrder = (typeof taskOrders)[number];
export const taskOrderLabels: Readonly<Record<TaskOrder, string>> = {
  MANUAL: 'Manual order',
  PRIORITY_HIGH_TO_LOW: 'Priority: High to Low',
  PRIORITY_LOW_TO_HIGH: 'Priority: Low to High',
  DUE_DATE_ASC: 'Due date: Soonest first',
  DUE_DATE_DESC: 'Due date: Latest first',
  CREATED_NEWEST: 'Created: Newest first',
  CREATED_OLDEST: 'Created: Oldest first',
  UPDATED_NEWEST: 'Updated: Newest first',
  UPDATED_OLDEST: 'Updated: Oldest first',
};
const priorityRank: Readonly<Record<TaskPriority, number>> = { LOW: 1, MEDIUM: 2, HIGH: 3 };
const compareText = (a: string, b: string): number => (a < b ? -1 : a > b ? 1 : 0);
const compareManual = (a: Task, b: Task): number =>
  a.position - b.position || compareText(a.id, b.id);
// Trusted server instants; invalid values use a deterministic epoch fallback.
const instant = (value: string): number => Date.parse(value) || 0;

function comparePrimary(a: Task, b: Task, order: TaskOrder): number {
  switch (order) {
    case 'MANUAL':
      return 0;
    case 'PRIORITY_HIGH_TO_LOW':
      return priorityRank[b.priority] - priorityRank[a.priority];
    case 'PRIORITY_LOW_TO_HIGH':
      return priorityRank[a.priority] - priorityRank[b.priority];
    case 'DUE_DATE_ASC':
    case 'DUE_DATE_DESC':
      if (a.dueDate === null) return b.dueDate === null ? 0 : 1;
      if (b.dueDate === null) return -1;
      return compareText(a.dueDate, b.dueDate) * (order === 'DUE_DATE_ASC' ? 1 : -1);
    case 'CREATED_NEWEST':
    case 'CREATED_OLDEST':
    case 'UPDATED_NEWEST':
    case 'UPDATED_OLDEST': {
      const field = order.startsWith('CREATED') ? 'createdAt' : 'updatedAt';
      return (instant(a[field]) - instant(b[field])) * (order.endsWith('NEWEST') ? -1 : 1);
    }
  }
}

/** Sort one Column's filtered Tasks on a copy, retaining canonical object identities. */
export function sortTasks(tasks: readonly Task[], order: TaskOrder): readonly Task[] {
  return [...tasks].sort((a, b) => comparePrimary(a, b, order) || compareManual(a, b));
}
