import { CdkDrag, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, computed, inject, input } from '@angular/core';
import { WorkspaceColumn } from '../boards/board-workspace/board-workspace-state';
import { TaskManagement } from './task-management';
import { TaskForm } from './task-form/task-form';
import { TaskPriorityIndicator } from './task-priority-indicator';
import { TaskPriorityView } from './task-priority-view';
import { projectTasksByPriority } from './task-priority';
import { UpdateTaskRequest } from './task.models';

@Component({
  selector: 'app-task-list',
  imports: [TaskForm, TaskPriorityIndicator, CdkDrag, CdkDragHandle, CdkDropList],
  templateUrl: './task-list.html',
  styleUrl: './task-controls.scss',
})
export class TaskList {
  readonly lane = input.required<WorkspaceColumn>();
  readonly dropData = computed(() => ({
    columnId: this.lane().column.id,
    tasks: this.lane().tasks,
  }));
  readonly management = inject(TaskManagement);
  readonly view = inject(TaskPriorityView);
  readonly visibleTasks = computed(() =>
    projectTasksByPriority(this.lane().tasks, this.view.filter(), this.view.order()),
  );
  readonly dragDisabled = computed(() => this.management.busy() || !this.view.manual());
  readonly create = (request: UpdateTaskRequest) =>
    this.management.create(this.lane().column.id, request);
}
