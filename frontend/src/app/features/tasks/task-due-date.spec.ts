import {
  DueDateFilter,
  filterTasksByDueDate,
  formatDueDate,
  localCalendarDate,
  parseLocalDate,
  taskDueState,
} from './task-due-date';
import { Task } from './task.models';
import { projectTasksByPriority } from './task-priority';

describe('Date-only due-date semantics', () => {
  it.each([
    [null, '2026-09-12', 'NO_DUE_DATE'],
    ['2026-09-11', '2026-09-12', 'OVERDUE'],
    ['2026-09-12', '2026-09-12', 'DUE_TODAY'],
    ['2026-09-13', '2026-09-12', 'UPCOMING'],
    ['2026-08-31', '2026-09-01', 'OVERDUE'],
    ['2026-12-31', '2027-01-01', 'OVERDUE'],
    ['2024-02-29', '2024-03-01', 'OVERDUE'],
    ['2024-02-29', '2024-02-28', 'UPCOMING'],
  ])('classifies %s against local day %s as %s', (date, today, expected) => {
    expect(taskDueState(date, today!)).toBe(expected);
  });
  it('uses local components and never an ISO UTC date', () => {
    const local = new Date(2026, 8, 12, 23, 59, 59);
    vi.spyOn(local, 'toISOString').mockImplementation(() => {
      throw new Error('UTC is not local today');
    });
    expect(localCalendarDate(local)).toBe('2026-09-12');
    // Explicit component stubs make the UTC/local day mismatch independent of the host timezone.
    const boundary = new Date('2026-09-12T00:30:00Z');
    vi.spyOn(boundary, 'getFullYear').mockReturnValue(2026);
    vi.spyOn(boundary, 'getMonth').mockReturnValue(8);
    vi.spyOn(boundary, 'getDate').mockReturnValue(11);
    expect(localCalendarDate(boundary)).toBe('2026-09-11');
  });
  it.each(['2026-09-30', '2024-02-29', '2027-01-01'])(
    'formats %s on its stored day with a controlled timezone',
    (date) => {
      const Formatter = Intl.DateTimeFormat;
      const spy = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(function (locales, options) {
        return new Formatter(locales, options);
      });
      const text = formatDueDate(date, 'en-US');
      expect(text).toBe(
        date === '2026-09-30'
          ? 'Sep 30, 2026'
          : date === '2024-02-29'
            ? 'Feb 29, 2024'
            : 'Jan 1, 2027',
      );
      expect(spy).toHaveBeenCalledWith('en-US', expect.objectContaining({ timeZone: 'UTC' }));
      spy.mockRestore();
    },
  );
  it('allows localized formatting and parses date-only components', () => {
    expect(parseLocalDate('2026-09-30')).toEqual({ year: 2026, month: 9, day: 30 });
    expect(formatDueDate('2026-09-30', 'en-GB')).toBe('30 Sept 2026');
    expect(parseLocalDate('2000-02-29').day).toBe(29);
  });
  it.each([
    '2026-02-29',
    '1900-02-29',
    '2026-04-31',
    '2026-00-01',
    '2026-13-01',
    '2026-01-00',
    '2026-9-30',
    '2026-09-30T00:00:00Z',
  ])('rejects invalid date-only input %s', (date) => {
    expect(() => parseLocalDate(date)).toThrow(RangeError);
  });
});

describe('Due-date projection', () => {
  const tasks: readonly Task[] = Object.freeze(
    [null, '2026-09-11', '2026-09-12', '2026-09-13'].map((dueDate, position) =>
      Object.freeze({
        id: 'ABCD'[position],
        columnId: 'done',
        title: 'Task',
        priority: 'MEDIUM' as const,
        dueDate,
        position,
        description: null,
        createdAt: '',
        updatedAt: '',
      }),
    ),
  );
  it.each<[DueDateFilter, string]>([
    ['ALL', 'ABCD'],
    ['OVERDUE', 'B'],
    ['DUE_TODAY', 'C'],
    ['UPCOMING', 'D'],
    ['NO_DUE_DATE', 'A'],
  ])('filters %s without mutating or inferring completion', (filter, expected) => {
    const before = structuredClone(tasks);
    const result = filterTasksByDueDate(tasks, filter, '2026-09-12');
    expect(result.map((t) => t.id).join('')).toBe(expected);
    expect(result).not.toBe(tasks);
    expect(tasks).toEqual(before);
    for (const task of result) expect(task).toBe(tasks.find((t) => t.id === task.id));
  });
  it('allows priority ordering of overdue Tasks, preserving equal-priority positions', () => {
    const input: Task[] = (['LOW', 'HIGH', 'MEDIUM', 'HIGH'] as const).map(
      (priority, position) => ({ ...tasks[1], id: 'ABCD'[position], position, priority }),
    );
    input.push({ ...tasks[3], id: 'future', priority: 'HIGH' });
    const before = structuredClone(input);
    const projected = projectTasksByPriority(
      filterTasksByDueDate(input, 'OVERDUE', '2026-09-12'),
      'ALL',
      'PRIORITY_HIGH_TO_LOW',
    );
    expect(projected.map((t) => t.id).join('')).toBe('BDCA');
    expect(input).toEqual(before);
  });
});
