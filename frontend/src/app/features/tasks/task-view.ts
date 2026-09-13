import { computed, effect, Injectable, signal } from '@angular/core';
import { BoardWorkspaceState } from '../boards/board-workspace/board-workspace-state';
import { DueDateFilter, dueDateFilters } from './task-due-date';
import { Task } from './task.models';
import { PriorityFilter, TaskOrder, taskPriorities } from './task-priority';
import { projectTasks } from './task-projection';
import { TaskSearch } from './task-search';

/** One Board page's presentation state; canonical workspace data remains read-only here. */
@Injectable()
export class TaskView {
  readonly search = new TaskSearch();
  private readonly source = signal<BoardWorkspaceState | null>(null);
  private readonly columnValue = signal('ALL');
  private readonly filterValue = signal<PriorityFilter>('ALL');
  private readonly dueDateValue = signal<DueDateFilter>('ALL');
  private readonly orderValue = signal<TaskOrder>('MANUAL');
  readonly filter = this.filterValue.asReadonly();
  readonly dueDateFilter = this.dueDateValue.asReadonly();
  readonly order = this.orderValue.asReadonly();
  readonly columns = computed(() => {
    const state = this.source()?.workspace();
    return state?.status === 'ready' ? state.columns.map((lane) => lane.column) : [];
  });
  readonly columnFilter = computed(() => {
    const state = this.source()?.workspace();
    const selected = this.columnValue();
    return state?.status === 'ready' && !state.columns.some((lane) => lane.column.id === selected)
      ? 'ALL'
      : selected;
  });
  readonly activeFilterCount = computed(
    () =>
      Number(this.search.active()) +
      Number(this.columnFilter() !== 'ALL') +
      Number(this.filter() !== 'ALL') +
      Number(this.dueDateFilter() !== 'ALL'),
  );
  readonly emptyMessage = computed(() =>
    this.search.active() && this.activeFilterCount() === 1
      ? 'No tasks match your search.'
      : 'No tasks match the current filters.',
  );
  readonly manual = computed(() => this.activeFilterCount() === 0 && this.order() === 'MANUAL');

  connect(workspace: BoardWorkspaceState): void {
    this.source.set(workspace);
    this.search.connect(workspace);
    effect(() => {
      // Keep the selection during loading; normalize only against a complete snapshot.
      if (workspace.workspace().status === 'ready' && this.columnValue() !== this.columnFilter())
        this.columnValue.set('ALL');
    });
  }
  setSearch(value: string): void {
    this.search.setInput(value);
  }
  setColumnFilter(value: string): void {
    this.columnValue.set(
      value === 'ALL' || this.columns().some((column) => column.id === value) ? value : 'ALL',
    );
  }
  setFilter(value: string): void {
    if (value === 'ALL' || taskPriorities.some((priority) => priority === value))
      this.filterValue.set(value as PriorityFilter);
  }
  setDueDateFilter(value: string): void {
    if (dueDateFilters.some((filter) => filter === value))
      this.dueDateValue.set(value as DueDateFilter);
  }
  setOrder(value: string): void {
    if (value === 'MANUAL' || value === 'PRIORITY_HIGH_TO_LOW' || value === 'PRIORITY_LOW_TO_HIGH')
      this.orderValue.set(value);
  }
  project(tasks: readonly Task[], today: string): readonly Task[] {
    return projectTasks(tasks, {
      searchActive: this.search.active(),
      searchMembership: this.search.membership(),
      columnId: this.columnFilter(),
      priority: this.filter(),
      dueDate: this.dueDateFilter(),
      order: this.order(),
      today,
    });
  }
  clearFilters(): void {
    this.search.clear();
    this.columnValue.set('ALL');
    this.filterValue.set('ALL');
    this.dueDateValue.set('ALL');
  }
  reset(): void {
    this.clearFilters();
    this.orderValue.set('MANUAL');
  }
}
