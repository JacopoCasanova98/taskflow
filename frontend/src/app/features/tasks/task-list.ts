import { CdkDrag, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, computed, inject, input } from '@angular/core';
import { WorkspaceColumn } from '../boards/board-workspace/board-workspace-state';
import { TaskManagement } from './task-management';
import { TaskPriorityIndicator } from './task-priority-indicator';
import { TaskDueIndicator } from './task-due-indicator';
import { TaskLocalDay } from './task-local-day';
import { TaskView } from './task-view';

@Component({
  selector: 'app-task-list',
  imports: [TaskPriorityIndicator, TaskDueIndicator, CdkDrag, CdkDragHandle, CdkDropList],
  templateUrl: './task-list.html',
  styleUrl: './task-list.scss',
})
export class TaskList {
  readonly lane = input.required<WorkspaceColumn>();
  readonly dropData = computed(() => ({
    columnId: this.lane().column.id,
    tasks: this.lane().tasks,
  }));
  readonly management = inject(TaskManagement);
  readonly view = inject(TaskView);
  private readonly localDay = inject(TaskLocalDay);
  readonly visibleTasks = computed(() =>
    this.view.project(this.lane().tasks, this.localDay.today()),
  );
  readonly dragDisabled = computed(() => this.management.busy() || !this.view.manual());
}
