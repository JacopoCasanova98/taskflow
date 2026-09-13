import { TaskSearch } from './task-search';
import { computed, Injectable, signal } from '@angular/core';
import { DueDateFilter, dueDateFilters } from './task-due-date';
import { PriorityFilter, TaskOrder, taskPriorities } from './task-priority';

/** Provided by one Board page; separate from canonical workspace state and shared write locking. */
@Injectable()
export class TaskView {
  readonly search = new TaskSearch();
  setSearch(value: string): void {
    if (value.trim()) {
      this.filterValue.set('ALL');
      this.dueDateValue.set('ALL');
    }
    this.search.setInput(value);
  }
  private readonly filterValue = signal<PriorityFilter>('ALL');
  private readonly dueDateValue = signal<DueDateFilter>('ALL');
  readonly dueDateFilter = this.dueDateValue.asReadonly();
  private readonly orderValue = signal<TaskOrder>('MANUAL');
  readonly filter = this.filterValue.asReadonly();
  readonly order = this.orderValue.asReadonly();
  readonly manual = computed(
    () =>
      !this.search.active() &&
      this.filter() === 'ALL' &&
      this.dueDateFilter() === 'ALL' &&
      this.order() === 'MANUAL',
  );

  setFilter(value: string): void {
    if (value === 'ALL' || taskPriorities.some((priority) => priority === value)) {
      if (value !== 'ALL') {
        this.search.clear();
        this.dueDateValue.set('ALL');
      }
      this.filterValue.set(value as PriorityFilter);
    }
  }
  setDueDateFilter(value: string): void {
    if (dueDateFilters.some((filter) => filter === value)) {
      if (value !== 'ALL') {
        this.search.clear();
        this.filterValue.set('ALL');
      }
      this.dueDateValue.set(value as DueDateFilter);
    }
  }
  setOrder(value: string): void {
    if (value === 'MANUAL' || value === 'PRIORITY_HIGH_TO_LOW' || value === 'PRIORITY_LOW_TO_HIGH')
      this.orderValue.set(value);
  }
  reset(): void {
    this.search.clear();
    this.filterValue.set('ALL');
    this.dueDateValue.set('ALL');
    this.orderValue.set('MANUAL');
  }
}
