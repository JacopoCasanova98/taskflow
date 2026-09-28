import { BoardStatisticsPanel } from '../statistics/board-statistics';
import { BoardStatisticsState } from '../statistics/board-statistics-state';
import { TaskLocalDay } from '../../tasks/task-local-day';
import { DialogRef } from '@angular/cdk/dialog';
import { CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, DestroyRef, effect, inject, Injector, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TaskView } from '../../tasks/task-view';
import { TaskViewControls } from '../../tasks/task-view-controls';
import { distinctUntilChanged, tap, map } from 'rxjs';
import { TaskList } from '../../tasks/task-list';
import { TaskManagement } from '../../tasks/task-management';
import { ColumnControls } from '../../columns/column-controls';
import { ColumnManagement } from '../../columns/column-management';
import { BoardWorkspaceState } from './board-workspace-state';
import { DialogFrame, TaskflowDialog } from '../../../shared/dialog/taskflow-dialog';
import { TaskDialog, TaskDialogData } from '../../tasks/task-dialog/task-dialog';

@Component({
  selector: 'app-board-workspace',
  imports: [
    BoardStatisticsPanel,
    RouterLink,
    ColumnControls,
    TaskList,
    CdkDropListGroup,
    TaskViewControls,
  ],
  providers: [
    BoardStatisticsState,
    BoardWorkspaceState,
    ColumnManagement,
    TaskManagement,
    TaskView,
    TaskLocalDay,
  ],
  templateUrl: './board-workspace.html',
  styleUrls: ['../board-controls.scss', './board-workspace.scss'],
})
export class BoardWorkspace {
  readonly taskView = inject(TaskView);
  readonly tasks = inject(TaskManagement);
  readonly columns = inject(ColumnManagement);
  readonly state = inject(BoardWorkspaceState);
  private readonly dialogs = inject(TaskflowDialog);
  private readonly injector = inject(Injector);
  private readonly activeDialog = signal<DialogRef<unknown, DialogFrame> | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.activeDialog()?.close());
    this.taskView.connect(this.state);
    this.state.connect(
      inject(ActivatedRoute).paramMap.pipe(
        map((params) => params.get('boardId')!),
        distinctUntilChanged(),
        tap(() => this.taskView.reset()),
      ),
    );
    effect(() => {
      const selected = this.tasks.selected();
      const creatingIn = this.tasks.creatingIn();
      if (this.activeDialog()) {
        if (!selected && !creatingIn) this.activeDialog()?.close();
        return;
      }
      if (selected)
        this.openTaskDialog({ kind: 'details', task: selected, management: this.tasks });
      else if (creatingIn) {
        const workspace = this.state.workspace();
        const column =
          workspace.status === 'ready'
            ? workspace.columns.find((lane) => lane.column.id === creatingIn)?.column
            : undefined;
        if (column)
          this.openTaskDialog({
            kind: 'create',
            columnId: column.id,
            columnName: column.name,
            management: this.tasks,
          });
      }
    });
  }

  private openTaskDialog(data: TaskDialogData): void {
    const ref = this.dialogs.open(TaskDialog, {
      title: data.kind === 'create' ? 'Create task' : data.task.title,
      size: 'lg',
      autoFocus: data.kind === 'create' ? 'input' : 'first-heading',
      data,
      injector: this.injector,
    });
    this.activeDialog.set(ref);
    ref.closed.subscribe(() => {
      if (data.kind === 'create') this.tasks.creatingIn.set(null);
      else if (this.tasks.selectedId() === data.task.id) this.tasks.selectedId.set(null);
      this.activeDialog.set(null);
    });
  }
}
