import { Component, computed, inject, input } from '@angular/core';
import { formatDueDate, taskDueState } from './task-due-date';
import { TaskLocalDay } from './task-local-day';

@Component({
  selector: 'app-task-due-indicator',
  templateUrl: './task-due-indicator.html',
  styleUrl: './task-due-indicator.scss',
})
export class TaskDueIndicator {
  readonly dueDate = input.required<string | null>();
  readonly showEmpty = input(false);
  private readonly localDay = inject(TaskLocalDay);
  readonly state = computed(() => taskDueState(this.dueDate(), this.localDay.today()));
  readonly formatted = computed(() => (this.dueDate() ? formatDueDate(this.dueDate()!) : ''));
}
