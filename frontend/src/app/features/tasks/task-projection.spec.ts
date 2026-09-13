import { Task } from './task.models';
import { projectTasks, TaskProjection } from './task-projection';

const tasks: readonly Task[] = Object.freeze(
  [
    { id: 'T1', columnId: 'a', priority: 'HIGH', dueDate: '2026-09-11', position: 0 },
    { id: 'T2', columnId: 'a', priority: 'LOW', dueDate: '2026-09-11', position: 1 },
    { id: 'T3', columnId: 'b', priority: 'HIGH', dueDate: '2026-09-11', position: 0 },
    { id: 'T4', columnId: 'b', priority: 'HIGH', dueDate: '2026-09-13', position: 1 },
    { id: 'T5', columnId: 'a', priority: 'HIGH', dueDate: '2026-09-11', position: 2 },
    { id: 'T6', columnId: 'a', priority: 'MEDIUM', dueDate: '2026-09-12', position: 3 },
    { id: 'T7', columnId: 'b', priority: 'LOW', dueDate: null, position: 2 },
  ].map((t) =>
    Object.freeze({ ...t, title: t.id, description: null, createdAt: '', updatedAt: '' } as Task),
  ),
);
const baseline: TaskProjection = {
  searchActive: false,
  searchMembership: new Set(['T1', 'T2', 'T3', 'T4']),
  columnId: 'ALL',
  priority: 'ALL',
  dueDate: 'ALL',
  order: 'MANUAL',
  today: '2026-09-12',
};

describe('Task AND projection', () => {
  it.each<[string, Partial<TaskProjection>, string[]]>([
    ['none', {}, ['T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']],
    ['Column', { columnId: 'a' }, ['T1', 'T2', 'T5', 'T6']],
    ['Priority', { priority: 'HIGH' }, ['T1', 'T3', 'T4', 'T5']],
    ['Due', { dueDate: 'OVERDUE' }, ['T1', 'T2', 'T3', 'T5']],
    ['Search', { searchActive: true }, ['T1', 'T2', 'T3', 'T4']],
    ['Column + Priority', { columnId: 'a', priority: 'HIGH' }, ['T1', 'T5']],
    ['Priority + Due', { priority: 'HIGH', dueDate: 'OVERDUE' }, ['T1', 'T3', 'T5']],
    ['Search + Priority', { searchActive: true, priority: 'HIGH' }, ['T1', 'T3', 'T4']],
    ['Search + Due', { searchActive: true, dueDate: 'OVERDUE' }, ['T1', 'T2', 'T3']],
    ['Search + Column', { searchActive: true, columnId: 'a' }, ['T1', 'T2']],
    [
      'Column + Priority + Due',
      { columnId: 'a', priority: 'HIGH', dueDate: 'OVERDUE' },
      ['T1', 'T5'],
    ],
    [
      'all four',
      { searchActive: true, columnId: 'a', priority: 'HIGH', dueDate: 'OVERDUE' },
      ['T1'],
    ],
    ['no due date', { dueDate: 'NO_DUE_DATE' }, ['T7']],
    ['today', { dueDate: 'DUE_TODAY' }, ['T6']],
    ['upcoming', { dueDate: 'UPCOMING' }, ['T4']],
    ['no match', { columnId: 'a', dueDate: 'UPCOMING' }, []],
  ])('applies %s without changing canonical Tasks', (_, changes, expected) => {
    const before = structuredClone(tasks);
    const result = projectTasks(tasks, { ...baseline, ...changes });
    expect(result.map((t) => t.id)).toEqual(expected);
    expect(result).not.toBe(tasks);
    for (const task of result) expect(task).toBe(tasks.find((t) => t.id === task.id));
    expect(tasks).toEqual(before);
  });
  it('applies local filters before the first Search result without client text matching', () => {
    expect(
      projectTasks(tasks, {
        ...baseline,
        searchActive: true,
        searchMembership: null,
        priority: 'LOW',
      }).map((t) => t.id),
    ).toEqual(['T2', 'T7']);
  });
  it('uses prior membership during Search loading and treats successful empty membership as empty', () => {
    expect(
      projectTasks(tasks, { ...baseline, searchActive: true, priority: 'LOW' }).map((t) => t.id),
    ).toEqual(['T2']);
    expect(
      projectTasks(tasks, { ...baseline, searchActive: true, searchMembership: new Set() }),
    ).toEqual([]);
  });
  it('filters before existing Priority order and preserves manual ties and positions', () => {
    const result = projectTasks(tasks, {
      ...baseline,
      columnId: 'a',
      dueDate: 'OVERDUE',
      order: 'PRIORITY_HIGH_TO_LOW',
    });
    expect(result.map((t) => t.id)).toEqual(['T1', 'T5', 'T2']);
    expect(result.map((t) => t.position)).toEqual([0, 2, 1]);
    expect(projectTasks(tasks, baseline)).toEqual(tasks);
  });
  it('recomputes combined due membership for the next local day', () => {
    const filters: TaskProjection = {
      ...baseline,
      columnId: 'a',
      priority: 'MEDIUM',
      dueDate: 'DUE_TODAY',
    };
    expect(projectTasks(tasks, filters).map((t) => t.id)).toEqual(['T6']);
    expect(projectTasks(tasks, { ...filters, today: '2026-09-13' })).toEqual([]);
  });
});
