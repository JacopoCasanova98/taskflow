import { Component, computed, inject, input } from '@angular/core';
import { WorkspaceColumn } from '../boards/board-workspace/board-workspace-state';
import { TaskManagement } from './task-management';
import { TaskForm } from './task-form/task-form';
import { TaskDetails } from './task-details/task-details';
import { UpdateTaskRequest } from './task.models';

@Component({
  selector: 'app-task-list',
  imports: [TaskForm, TaskDetails],
  templateUrl: './task-list.html',
  styleUrl: './task-controls.scss',
})
export class TaskList {
  readonly lane = input.required<WorkspaceColumn>();
  readonly management = inject(TaskManagement);
  readonly selected = computed(() => {
    const task = this.management.selected();
    return task?.columnId === this.lane().column.id ? task : null;
  });
  readonly create = (request: UpdateTaskRequest) =>
    this.management.create(this.lane().column.id, request);
}
