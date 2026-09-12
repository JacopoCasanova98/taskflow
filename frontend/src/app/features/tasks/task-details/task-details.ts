import { Component, inject, input, signal } from '@angular/core';
import { Task, UpdateTaskRequest } from '../task.models';
import { TaskManagement } from '../task-management';
import { TaskPriorityIndicator } from '../task-priority-indicator';
import { TaskForm } from '../task-form/task-form';
import { BoardWorkspaceState } from '../../boards/board-workspace/board-workspace-state';

@Component({
  selector: 'app-task-details',
  imports: [TaskForm, TaskPriorityIndicator],
  templateUrl: './task-details.html',
  styleUrl: '../task-controls.scss',
})
export class TaskDetails {
  readonly task = input.required<Task>();
  readonly management = inject(TaskManagement);
  private readonly workspace = inject(BoardWorkspaceState);
  readonly editing = signal(false);
  readonly confirming = signal(false);
  readonly error = signal('');
  readonly update = (request: UpdateTaskRequest) => this.management.update(this.task().id, request);

  async deleteTask(): Promise<void> {
    if (this.management.busy()) return;
    const generation = this.workspace.generation();
    this.error.set('');
    const result = await this.management.delete(this.task().id);
    if (generation === this.workspace.generation() && !result.success && !result.stale)
      this.error.set(result.error);
  }
}
