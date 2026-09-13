import { Component, inject } from '@angular/core';
import { TaskView } from './task-view';
import { dueDateFilters, dueDateFilterLabels } from './task-due-date';
import { priorityLabels } from './task-priority';

@Component({
  selector: 'app-task-view-controls',
  template: `
    <div
      class="controls"
      role="group"
      aria-label="Task view"
      [attr.aria-describedby]="view.manual() ? null : 'task-movement-guidance'"
    >
      <div>
        <label for="task-search">Search tasks</label>
        <input
          id="task-search"
          type="search"
          placeholder="Search title or description"
          #search
          [value]="view.search.input()"
          (input)="view.setSearch(search.value)"
          aria-describedby="task-search-help"
          [attr.aria-invalid]="view.search.query().length > 200 ? true : null"
        />
        <span id="task-search-help">Up to 200 characters.</span>
        @if (view.search.input()) {
          <button type="button" (click)="view.search.clear()">Clear search</button>
        }
      </div>
      <div>
        <label for="task-column-filter">Column</label>
        <select
          id="task-column-filter"
          #column
          [value]="view.columnFilter()"
          (change)="view.setColumnFilter(column.value)"
        >
          <option value="ALL">All columns</option>
          @for (column of view.columns(); track column.id) {
            <option [value]="column.id">{{ column.name }}</option>
          }
        </select>
      </div>
      <div>
        <label for="task-priority-filter">Priority</label>
        <select
          id="task-priority-filter"
          #filter
          [value]="view.filter()"
          (change)="view.setFilter(filter.value)"
        >
          <option value="ALL">All priorities</option>
          @for (priority of priorities; track priority) {
            <option [value]="priority">{{ labels[priority] }}</option>
          }
        </select>
      </div>
      <div>
        <label for="task-due-filter">Due date</label>
        <select
          id="task-due-filter"
          #due
          [value]="view.dueDateFilter()"
          (change)="view.setDueDateFilter(due.value)"
        >
          @for (filter of dueFilters; track filter) {
            <option [value]="filter">{{ dueLabels[filter] }}</option>
          }
        </select>
      </div>
      <div>
        <label for="task-priority-order">Task order</label>
        <select
          id="task-priority-order"
          #order
          [value]="view.order()"
          (change)="view.setOrder(order.value)"
        >
          <option value="MANUAL">Manual order</option>
          <option value="PRIORITY_HIGH_TO_LOW">Priority: High to Low</option>
          <option value="PRIORITY_LOW_TO_HIGH">Priority: Low to High</option>
        </select>
      </div>
      <div class="clear-filters">
        <button
          type="button"
          [disabled]="view.activeFilterCount() === 0"
          (click)="view.clearFilters()"
        >
          Clear filters
        </button>
      </div>
    </div>
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
      @if (view.search.query().length > 200) {
        <p role="alert">Use no more than 200 characters to search tasks.</p>
      } @else {
        <p role="alert">We couldn't search tasks. Please try again.</p>
        <button type="button" (click)="view.search.refresh()">Retry search</button>
      }
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
  readonly dueFilters = dueDateFilters;
  readonly dueLabels = dueDateFilterLabels;
  readonly labels = priorityLabels;
}
