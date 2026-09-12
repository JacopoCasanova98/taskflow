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
        <label for="task-priority-filter">Priority filter</label>
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
    </div>
    @if (!view.manual()) {
      <p id="task-movement-guidance" role="status">
        Task movement is available in manual order with all filters cleared.
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
