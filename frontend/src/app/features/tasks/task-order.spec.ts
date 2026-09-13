import { Task } from './task.models';
import { sortTasks, taskOrders, TaskOrder } from './task-order';
import { projectTasks } from './task-projection';

const tasks: readonly Task[] = Object.freeze(
  ['A', 'B', 'C', 'D', 'E'].map((id, position) =>
    Object.freeze({
      id,
      columnId: 'a',
      title: id,
      description: null,
      position,
      priority: (['LOW', 'HIGH', 'MEDIUM', 'HIGH', 'LOW'] as const)[position],
      dueDate: ['2026-09-20', null, '2026-09-10', null, '2026-09-15'][position],
      createdAt: '2026-09-12T' + ['10', '12', '11', '09', '08'][position] + ':00:00Z',
      updatedAt: '2026-09-12T' + ['13', '10', '12', '09', '08'][position] + ':00:00Z',
    }),
  ),
);

describe('Task sorting', () => {
  it.each<[TaskOrder, string]>([
    ['MANUAL', 'ABCDE'],
    ['PRIORITY_HIGH_TO_LOW', 'BDCAE'],
    ['PRIORITY_LOW_TO_HIGH', 'AECBD'],
    ['DUE_DATE_ASC', 'CEABD'],
    ['DUE_DATE_DESC', 'AECBD'],
    ['CREATED_NEWEST', 'BCADE'],
    ['CREATED_OLDEST', 'EDACB'],
    ['UPDATED_NEWEST', 'ACBDE'],
    ['UPDATED_OLDEST', 'EDBCA'],
  ])('sorts %s on a copy without changing canonical objects or positions', (order, expected) => {
    const before = structuredClone(tasks);
    const result = sortTasks(tasks, order);
    expect(result.map((t) => t.id).join('')).toBe(expected);
    expect(result).not.toBe(tasks);
    result.forEach((t) => expect(t).toBe(tasks.find((source) => source.id === t.id)));
    expect(tasks).toEqual(before);
  });
  it.each(taskOrders)('resolves %s ties by position then ID regardless of input order', (order) => {
    const input = [
      { ...tasks[0], id: 'Z', position: 2 },
      { ...tasks[0], id: 'B', position: 0 },
      { ...tasks[0], id: 'C', position: 1 },
      { ...tasks[0], id: 'A', position: 0 },
    ];
    expect(sortTasks(input, order).map((t) => t.id)).toEqual(['A', 'B', 'C', 'Z']);
  });
  it.each(['DUE_DATE_ASC', 'DUE_DATE_DESC'] as const)(
    'uses manual and ID ties for null dates in %s',
    (order) => {
      const input = [
        { ...tasks[0], id: 'Z', dueDate: null },
        { ...tasks[0], id: 'A', dueDate: null },
        tasks[2],
      ];
      expect(sortTasks(input, order).map((t) => t.id)).toEqual(['C', 'A', 'Z']);
    },
  );
  it.each(['CREATED_NEWEST', 'UPDATED_NEWEST'] as const)(
    'compares actual instants including offsets for %s',
    (order) => {
      const input = [
        {
          ...tasks[0],
          createdAt: '2026-09-12T12:00:00+02:00',
          updatedAt: '2026-09-12T12:00:00+02:00',
        },
        { ...tasks[1], createdAt: '2026-09-12T11:00:00Z', updatedAt: '2026-09-12T11:00:00Z' },
      ];
      expect(sortTasks(input, order).map((t) => t.id)).toEqual(['B', 'A']);
    },
  );
  it('uses deterministic fallback for invalid timestamps', () => {
    const input = [
      { ...tasks[1], createdAt: 'invalid' },
      { ...tasks[0], createdAt: '' },
    ];
    expect(sortTasks(input, 'CREATED_NEWEST').map((t) => t.id)).toEqual(['A', 'B']);
  });
  it('restores manual order after every projection', () => {
    for (const order of taskOrders)
      expect(sortTasks(sortTasks(tasks, order), 'MANUAL')).toEqual(tasks);
  });
  it('applies all four membership filters before Updated newest', () => {
    const input = tasks.map((t) => ({ ...t, priority: 'HIGH' as const }));
    expect(
      projectTasks(input, {
        searchActive: true,
        searchMembership: new Set(['A', 'B', 'C', 'D']),
        columnId: 'a',
        priority: 'HIGH',
        dueDate: 'OVERDUE',
        order: 'UPDATED_NEWEST',
        today: '2026-10-01',
      }).map((t) => t.id),
    ).toEqual(['A', 'C']);
  });
});
