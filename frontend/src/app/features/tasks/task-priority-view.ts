import { computed, Injectable, signal } from '@angular/core';
import { PriorityFilter, TaskOrder, taskPriorities } from './task-priority';

/** Provided by one Board page; separate from canonical workspace state and shared write locking. */
@Injectable()
export class TaskPriorityView {
  private readonly filterValue = signal<PriorityFilter>('ALL');
  private readonly orderValue = signal<TaskOrder>('MANUAL');
  readonly filter = this.filterValue.asReadonly();
  readonly order = this.orderValue.asReadonly();
  readonly manual = computed(() => this.filter() === 'ALL' && this.order() === 'MANUAL');

  setFilter(value: string): void {
    if (value === 'ALL' || taskPriorities.some((priority) => priority === value))
      this.filterValue.set(value as PriorityFilter);
  }
  setOrder(value: string): void {
    if (value === 'MANUAL' || value === 'PRIORITY_HIGH_TO_LOW' || value === 'PRIORITY_LOW_TO_HIGH')
      this.orderValue.set(value);
  }
  reset(): void {
    this.filterValue.set('ALL');
    this.orderValue.set('MANUAL');
  }
}
