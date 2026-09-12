import { Component, input } from '@angular/core';
import { TaskPriority } from './task.models';
import { priorityLabels } from './task-priority';

@Component({
  selector: 'app-task-priority-indicator',
  template: `<span
    class="priority"
    [class.high]="priority() === 'HIGH'"
    [class.medium]="priority() === 'MEDIUM'"
    [class.low]="priority() === 'LOW'"
  >
    <span class="sr-only">Priority: </span>{{ labels[priority()] }}
  </span>`,
  styleUrl: './task-priority-indicator.scss',
})
export class TaskPriorityIndicator {
  readonly priority = input.required<TaskPriority>();
  readonly labels = priorityLabels;
}
