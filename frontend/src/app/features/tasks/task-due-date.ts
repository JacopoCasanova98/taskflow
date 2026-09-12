import { Task } from './task.models';

export type TaskDueState = 'NO_DUE_DATE' | 'OVERDUE' | 'DUE_TODAY' | 'UPCOMING';
export type DueDateFilter = 'ALL' | TaskDueState;
export const dueDateFilters: readonly DueDateFilter[] = [
  'ALL',
  'OVERDUE',
  'DUE_TODAY',
  'UPCOMING',
  'NO_DUE_DATE',
];
export const dueDateFilterLabels: Readonly<Record<DueDateFilter, string>> = {
  ALL: 'All due dates',
  OVERDUE: 'Overdue',
  DUE_TODAY: 'Due today',
  UPCOMING: 'Upcoming',
  NO_DUE_DATE: 'No due date',
};

/** Browser-local civil day, not the UTC day of an instant. */
export function localCalendarDate(now: Date = new Date()): string {
  return [
    String(now.getFullYear()).padStart(4, '0'),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
}

/** Inputs are normalized calendar dates from the LocalDate API, never instants. */
export function taskDueState(dueDate: string | null, today: string): TaskDueState {
  if (dueDate === null) return 'NO_DUE_DATE';
  return dueDate < today ? 'OVERDUE' : dueDate === today ? 'DUE_TODAY' : 'UPCOMING';
}

export function parseLocalDate(value: string): { year: number; month: number; day: number } {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) throw new RangeError('Expected a calendar date in YYYY-MM-DD format.');
  const [year, month, day] = match.slice(1).map(Number);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month < 1 || month > 12 || day < 1 || day > days[month - 1])
    throw new RangeError('Invalid calendar date.');
  return { year, month, day };
}

/** UTC is only a formatting carrier: the API date is never converted to the user's timezone. */
export function formatDueDate(value: string, locale?: string): string {
  const { year, month, day } = parseLocalDate(value);
  const carrier = new Date(0);
  carrier.setUTCFullYear(year, month - 1, day);
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(carrier);
}

export function filterTasksByDueDate(
  tasks: readonly Task[],
  filter: DueDateFilter,
  today: string,
): readonly Task[] {
  return tasks.filter((task) => filter === 'ALL' || taskDueState(task.dueDate, today) === filter);
}
