import { planTaskPlacement } from './task-placement';
import { WorkspaceColumn } from '../boards/board-workspace/board-workspace-state';

function lane(id: string, count: number): WorkspaceColumn {
  return {
    column: { id, name: id, position: 0, createdAt: '', updatedAt: '' },
    tasks: Array.from({ length: count }, (_, position) => ({
      id: id + position,
      columnId: id,
      position,
      title: 'Title',
      description: 'Detail',
      priority: 'HIGH',
      dueDate: '2026-09-30',
      createdAt: 'created',
      updatedAt: 'updated',
    })),
  };
}
describe('Task placement planning', () => {
  it.each([
    [1, 0, ['a1', 'a0', 'a2']],
    [0, 2, ['a1', 'a2', 'a0']],
    [2, 0, ['a2', 'a0', 'a1']],
    [0, 1, ['a1', 'a0', 'a2']],
    [2, 1, ['a0', 'a2', 'a1']],
  ])('reorders %s to %s immutably', (from, to, expected) => {
    const columns = [lane('a', 3), lane('b', 2)];
    const before = structuredClone(columns);
    const plan = planTaskPlacement(columns, 'a', 'a', 'a' + from, from as number, to as number)!;
    expect(plan.columns[0].tasks.map((task) => task.id)).toEqual(expected);
    expect(plan.columns[0].tasks.map((task) => task.position)).toEqual([0, 1, 2]);
    expect(plan.columns[1]).toBe(columns[1]);
    expect(plan.snapshot).toEqual([columns[0]]);
    expect(columns).toEqual(before);
    for (const task of plan.columns[0].tasks)
      expect(task).toEqual({
        ...columns[0].tasks.find((entry) => entry.id === task.id),
        position: task.position,
      });
  });
  it.each([0, 1, 2])('moves across Columns to index %s', (position) => {
    const columns = [lane('a', 3), lane('b', 2), lane('c', 1)];
    const before = structuredClone(columns);
    const plan = planTaskPlacement(columns, 'a', 'b', 'a1', 1, position)!;
    const expected = ['b0', 'b1'];
    expected.splice(position, 0, 'a1');
    expect(plan.columns[0].tasks.map((task) => task.id)).toEqual(['a0', 'a2']);
    expect(plan.columns[1].tasks.map((task) => task.id)).toEqual(expected);
    expect(plan.columns[0].tasks.map((task) => task.position)).toEqual([0, 1]);
    expect(plan.columns[1].tasks.map((task) => task.position)).toEqual([0, 1, 2]);
    expect(plan.columns[1].tasks[position]).toEqual({
      ...columns[0].tasks[1],
      columnId: 'b',
      position,
    });
    expect(plan.columns[2]).toBe(columns[2]);
    expect(plan.snapshot).toEqual(columns.slice(0, 2));
    expect(columns).toEqual(before);
  });
  it('accepts an empty Column at zero', () => {
    const plan = planTaskPlacement([lane('a', 3), lane('b', 0)], 'a', 'b', 'a1', 1, 0)!;
    expect(plan.columns[1].tasks).toHaveLength(1);
    expect(plan.request).toEqual({ columnId: 'b', position: 0 });
  });
  it.each([
    ['a', 'a', 'a1', 1, 1],
    ['external', 'b', 'a1', 1, 0],
    ['a', 'external', 'a1', 1, 0],
    ['a', 'b', 'wrong', 1, 0],
    ['a', 'b', 'a1', 1, -1],
    ['a', 'b', 'a1', 1, 3],
    ['a', 'a', 'a1', 1, 3],
    ['a', 'b', 'a1', 1, 0.5],
    ['a', 'b', 'a1', -1, 0],
    ['a', 'b', 'a1', 0.5, 0],
  ] as const)(
    'rejects invalid/no-op coordinates %s %s %s %s %s',
    (source, target, id, from, to) => {
      expect(
        planTaskPlacement([lane('a', 3), lane('b', 2)], source, target, id, from, to),
      ).toBeNull();
    },
  );
});
