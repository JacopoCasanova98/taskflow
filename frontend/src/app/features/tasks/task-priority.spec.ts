import { TestBed } from '@angular/core/testing';
import { Task, TaskPriority } from './task.models';
import { PriorityFilter, projectTasksByPriority, TaskOrder } from './task-priority';
import { TaskPriorityIndicator } from './task-priority-indicator';

const tasks: readonly Task[] = (['LOW', 'HIGH', 'MEDIUM', 'HIGH'] as const).map(
  (priority, position) =>
    Object.freeze({
      id: 'ABCD'[position],
      columnId: 'column',
      title: 'Title',
      description: null,
      priority,
      position,
      dueDate: null,
      createdAt: '',
      updatedAt: '',
    }),
);
Object.freeze(tasks);

describe('Priority projection', () => {
  it.each<[PriorityFilter, TaskOrder, string]>([
    ['ALL', 'MANUAL', 'ABCD'],
    ['HIGH', 'MANUAL', 'BD'],
    ['MEDIUM', 'MANUAL', 'C'],
    ['LOW', 'MANUAL', 'A'],
    ['ALL', 'PRIORITY_HIGH_TO_LOW', 'BDCA'],
    ['ALL', 'PRIORITY_LOW_TO_HIGH', 'ACBD'],
    ['HIGH', 'PRIORITY_HIGH_TO_LOW', 'BD'],
    ['HIGH', 'PRIORITY_LOW_TO_HIGH', 'BD'],
  ])('projects %s / %s without mutating canonical Tasks', (filter, order, expected) => {
    const before = structuredClone(tasks);
    const result = projectTasksByPriority(tasks, filter, order);
    expect(result.map((task) => task.id).join('')).toBe(expected);
    expect(result).not.toBe(tasks);
    expect(tasks).toEqual(before);
    for (const task of result) expect(task).toBe(tasks.find((entry) => entry.id === task.id));
  });
  it('uses manual positions before IDs to break equal-priority ties', () => {
    const input = [tasks[3], tasks[1]];
    expect(projectTasksByPriority(input, 'ALL', 'PRIORITY_HIGH_TO_LOW')).toEqual([
      tasks[1],
      tasks[3],
    ]);
    expect(projectTasksByPriority(input, 'ALL', 'PRIORITY_LOW_TO_HIGH')).toEqual([
      tasks[1],
      tasks[3],
    ]);
    expect(projectTasksByPriority(input, 'ALL', 'MANUAL')).toEqual([tasks[1], tasks[3]]);
  });
  it('uses IDs for equal ranks and positions', () => {
    const input = [
      { ...tasks[1], id: 'Z' },
      { ...tasks[1], id: 'A' },
    ];
    expect(projectTasksByPriority(input, 'ALL', 'PRIORITY_HIGH_TO_LOW')).toEqual([
      input[1],
      input[0],
    ]);
  });
  it('handles actual and filtered empty arrays', () => {
    expect(projectTasksByPriority([], 'ALL', 'MANUAL')).toEqual([]);
    expect(projectTasksByPriority([tasks[0]], 'HIGH', 'PRIORITY_HIGH_TO_LOW')).toEqual([]);
  });
});

describe('Task priority indicator', () => {
  it.each<[TaskPriority, string]>([
    ['LOW', 'Low'],
    ['MEDIUM', 'Medium'],
    ['HIGH', 'High'],
  ])('renders accessible human text for %s without relying on CSS', async (priority, label) => {
    const fixture = TestBed.createComponent(TaskPriorityIndicator);
    fixture.componentRef.setInput('priority', priority);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent?.trim()).toBe('Priority: ' + label);
    expect(element.querySelector('.priority')?.classList.contains(priority.toLowerCase())).toBe(
      true,
    );
    expect(element.querySelector('[aria-hidden="true"]')).toBeNull();
    expect(element.textContent).not.toMatch(/LOW|MEDIUM|HIGH|rank|[123]/);
  });
});
