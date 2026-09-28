import {
  afterNextRender,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TaskForm } from '../task-form/task-form';
import { TaskDueIndicator } from '../task-due-indicator';
import { TaskPriorityIndicator } from '../task-priority-indicator';
import { Task, UpdateTaskRequest } from '../task.models';
import { TaskManagement } from '../task-management';
import { BoardWorkspaceState } from '../../boards/board-workspace/board-workspace-state';
import { DialogFrame } from '../../../shared/dialog/taskflow-dialog';

export type TaskDialogData = { management: TaskManagement } & (
  { kind: 'create'; columnId: string; columnName: string } | { kind: 'details'; task: Task }
);

@Component({
  selector: 'app-task-dialog',
  imports: [TaskDueIndicator, TaskForm, TaskPriorityIndicator],
  templateUrl: './task-dialog.html',
  styleUrl: './task-dialog.scss',
})
export class TaskDialog {
  readonly data = inject<TaskDialogData>(DIALOG_DATA);
  readonly ref = inject<DialogRef<unknown, DialogFrame>>(DialogRef);
  readonly management = this.data.management;
  readonly editing = signal(false);
  readonly confirming = signal(false);
  readonly deleteError = signal('');
  private readonly workspace = inject(BoardWorkspaceState);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  readonly task = computed(() =>
    this.data.kind === 'details'
      ? (this.management.find(this.data.task.id) ?? this.data.task)
      : null,
  );
  readonly columnName = computed(() => {
    const workspace = this.workspace.workspace();
    return workspace.status === 'ready'
      ? (workspace.columns.find(
          (lane) =>
            lane.column.id ===
            (this.task()?.columnId ?? (this.data.kind === 'create' ? this.data.columnId : '')),
        )?.column.name ?? '')
      : '';
  });

  constructor() {
    effect(() => {
      this.ref.disableClose = this.management.busy();
      const title = this.confirming()
        ? 'Delete task?'
        : this.editing()
          ? 'Edit task'
          : (this.task()?.title ?? 'Create task');
      this.ref.componentInstance?.title.set(title);
      this.ref.overlayRef.overlayElement
        .querySelector('[role="dialog"]')
        ?.setAttribute('aria-label', title);
      this.ref.removePanelClass(['tf-dialog-lg', 'tf-dialog-sm']);
      this.ref.addPanelClass(this.confirming() ? 'tf-dialog-sm' : 'tf-dialog-lg');
      this.ref.addPanelClass('tf-task-dialog');
    });
  }

  setMode(mode: 'details' | 'edit' | 'delete'): void {
    this.editing.set(mode === 'edit');
    this.confirming.set(mode === 'delete');
    this.deleteError.set('');
    afterNextRender(
      () => {
        this.element.nativeElement
          .querySelector<HTMLElement>(
            mode === 'delete'
              ? '[data-cancel-delete]'
              : mode === 'edit'
                ? 'input'
                : '[data-edit-task]',
          )
          ?.focus();
      },
      { injector: this.injector },
    );
  }
  readonly update = (request: UpdateTaskRequest) =>
    this.data.kind === 'details'
      ? this.management.update(this.data.task.id, request)
      : Promise.resolve({ success: false as const, stale: true, error: '' });
  readonly create = (request: UpdateTaskRequest) =>
    this.data.kind === 'create'
      ? this.management.create(this.data.columnId, request)
      : Promise.resolve({ success: false as const, stale: true, error: '' });

  close(): void {
    this.ref.close();
  }

  async deleteTask(): Promise<void> {
    if (this.data.kind !== 'details' || this.management.busy()) return;
    this.deleteError.set('');
    this.ref.disableClose = true;
    const result = await this.management.delete(this.data.task.id);
    this.ref.disableClose = false;
    if (result.success || result.stale) this.ref.close();
    else this.deleteError.set(result.error);
  }
}
