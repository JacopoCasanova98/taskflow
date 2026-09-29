import { CdkMenu, CdkMenuItemRadio, CdkMenuTrigger } from '@angular/cdk/menu';
import { Component, inject } from '@angular/core';
import { Icon } from '../../shared/icon/icon';
import { dueDateFilters, dueDateFilterLabels } from './task-due-date';
import { priorityLabels } from './task-priority';
import { taskOrders, taskOrderLabels } from './task-order';
import { TaskView } from './task-view';

@Component({
  selector: 'app-task-view-controls',
  imports: [CdkMenu, CdkMenuItemRadio, CdkMenuTrigger, Icon],
  template: `
    <section
      class="task-view"
      aria-label="Task filters"
      [attr.aria-describedby]="view.manual() ? null : 'task-movement-guidance'"
    >
      <div class="search-shell">
        <app-icon name="search" /><label class="sr-only" for="task-search">Search tasks</label
        ><input
          id="task-search"
          type="search"
          placeholder="Search tasks"
          #search
          [value]="view.search.input()"
          (input)="view.setSearch(search.value)"
          aria-describedby="task-search-help"
          [attr.aria-invalid]="view.search.query().length > 200 ? true : null"
        />
        @if (view.search.input()) {
          <button
            class="clear-search"
            aria-label="Clear search"
            type="button"
            (click)="view.search.clear()"
          >
            Clear
          </button>
        }
      </div>
      <span id="task-search-help" class="sr-only">Up to 200 characters.</span>
      <div class="filter-row">
        <button
          id="task-column-filter"
          [value]="view.columnFilter()"
          class="filter-trigger"
          type="button"
          [class.active]="view.columnFilter() !== 'ALL'"
          [cdkMenuTriggerFor]="columnMenu"
        >
          {{ columnText() }} <app-icon name="chevron-down" />
        </button>
        <button
          id="task-priority-filter"
          [value]="view.filter()"
          class="filter-trigger"
          type="button"
          [class.active]="view.filter() !== 'ALL'"
          [cdkMenuTriggerFor]="priorityMenu"
        >
          {{ priorityText() }} <app-icon name="chevron-down" />
        </button>
        <button
          id="task-due-filter"
          [value]="view.dueDateFilter()"
          class="filter-trigger"
          type="button"
          [class.active]="view.dueDateFilter() !== 'ALL'"
          [cdkMenuTriggerFor]="dueMenu"
        >
          {{ dueText() }} <app-icon name="chevron-down" />
        </button>
        <button
          id="task-priority-order"
          [value]="view.order()"
          class="filter-trigger"
          type="button"
          [class.active]="view.order() !== 'MANUAL'"
          [cdkMenuTriggerFor]="sortMenu"
        >
          {{ sortText() }} <app-icon name="chevron-down" />
        </button>
        @if (view.activeFilterCount()) {
          <button class="clear-filters" type="button" (click)="view.clearFilters()">
            Clear filters
          </button>
        }
      </div>
    </section>
    <ng-template #columnMenu
      ><div cdkMenu class="tf-menu filter-menu" aria-label="Column filter">
        <button
          cdkMenuItemRadio
          value="ALL"
          [cdkMenuItemChecked]="view.columnFilter() === 'ALL'"
          type="button"
          (cdkMenuItemTriggered)="view.setColumnFilter('ALL')"
        >
          All columns
        </button>
        @for (column of view.columns(); track column.id) {
          <button
            cdkMenuItemRadio
            [value]="column.id"
            [cdkMenuItemChecked]="view.columnFilter() === column.id"
            type="button"
            (cdkMenuItemTriggered)="view.setColumnFilter(column.id)"
          >
            {{ column.name }}
          </button>
        }
      </div></ng-template
    >
    <ng-template #priorityMenu
      ><div cdkMenu class="tf-menu filter-menu" aria-label="Priority filter">
        <button
          cdkMenuItemRadio
          value="ALL"
          [cdkMenuItemChecked]="view.filter() === 'ALL'"
          type="button"
          (cdkMenuItemTriggered)="view.setFilter('ALL')"
        >
          All priorities
        </button>
        @for (priority of priorities; track priority) {
          <button
            cdkMenuItemRadio
            [value]="priority"
            [cdkMenuItemChecked]="view.filter() === priority"
            type="button"
            (cdkMenuItemTriggered)="view.setFilter(priority)"
          >
            {{ labels[priority] }}
          </button>
        }
      </div></ng-template
    >
    <ng-template #dueMenu
      ><div cdkMenu class="tf-menu filter-menu" aria-label="Due date filter">
        @for (filter of dueFilters; track filter) {
          <button
            cdkMenuItemRadio
            [value]="filter"
            [cdkMenuItemChecked]="view.dueDateFilter() === filter"
            type="button"
            (cdkMenuItemTriggered)="view.setDueDateFilter(filter)"
          >
            {{ dueLabels[filter] }}
          </button>
        }
      </div></ng-template
    >
    <ng-template #sortMenu
      ><div cdkMenu class="tf-menu filter-menu" aria-label="Sort tasks">
        @for (order of orders; track order) {
          <button
            cdkMenuItemRadio
            [value]="order"
            [cdkMenuItemChecked]="view.order() === order"
            type="button"
            (cdkMenuItemTriggered)="view.setOrder(order)"
          >
            {{ orderLabels[order] }}
          </button>
        }
      </div></ng-template
    >
    <p role="status">
      @if (view.activeFilterCount(); as count) {
        {{ count }} {{ count === 1 ? 'filter active' : 'filters active' }}
      }
    </p>
    @if (view.search.empty()) {
      <p role="status">{{ view.emptyMessage() }}</p>
    }
    @if (view.search.status() === 'loading') {
      <p role="status">Searching…</p>
    }
    @if (view.search.status() === 'error') {
      <p role="alert">We couldn't search tasks. Please try again.</p>
      <button class="clear-filters" type="button" (click)="view.search.refresh()">
        Retry search
      </button>
    }
    @if (!view.manual()) {
      <p id="task-movement-guidance" role="status">
        Task movement is available in manual order with search and all filters cleared.
      </p>
    }
  `,
  styleUrl: './task-view-controls.scss',
})
export class TaskViewControls {
  readonly view = inject(TaskView);
  readonly priorities = ['HIGH', 'MEDIUM', 'LOW'] as const;
  readonly labels = priorityLabels;
  readonly dueFilters = dueDateFilters;
  readonly dueLabels = dueDateFilterLabels;
  readonly orders = taskOrders;
  readonly orderLabels = taskOrderLabels;
  columnText() {
    const value = this.view.columnFilter();
    return value === 'ALL'
      ? 'Column'
      : 'Column: ' + (this.view.columns().find((column) => column.id === value)?.name ?? 'Column');
  }
  priorityText() {
    const value = this.view.filter();
    return value === 'ALL' ? 'Priority' : 'Priority: ' + this.labels[value];
  }
  dueText() {
    return this.view.dueDateFilter() === 'ALL'
      ? 'Due date'
      : 'Due date: ' + this.dueLabels[this.view.dueDateFilter()];
  }
  sortText() {
    return this.view.order() === 'MANUAL' ? 'Sort' : 'Sort: ' + this.orderLabels[this.view.order()];
  }
}
