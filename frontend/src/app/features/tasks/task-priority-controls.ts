import { Component, inject } from '@angular/core';
import { TaskPriorityView } from './task-priority-view';
import { priorityLabels } from './task-priority';

@Component({
  selector: 'app-task-priority-controls',
  template: `
    <div
      class="controls"
      role="group"
      aria-label="Priority view"
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
        Task movement is available in manual order with all priorities visible.
      </p>
    }
  `,
  styleUrl: './task-priority-controls.scss',
})
export class TaskPriorityControls {
  readonly view = inject(TaskPriorityView);
  readonly priorities = ['HIGH', 'MEDIUM', 'LOW'] as const;
  readonly labels = priorityLabels;
}
