import { NgTemplateOutlet } from '@angular/common';
import { DialogRef } from '@angular/cdk/dialog';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import {
  Component,
  computed,
  contentChild,
  DestroyRef,
  effect,
  inject,
  OnDestroy,
  signal,
  TemplateRef,
  untracked,
} from '@angular/core';
import {
  BoardWorkspaceState,
  WorkspaceColumn,
} from '../boards/board-workspace/board-workspace-state';
import { DialogFrame, TaskflowDialog } from '../../shared/dialog/taskflow-dialog';
import { Icon } from '../../shared/icon/icon';
import { IconButton } from '../../shared/icon/icon-button';
import { ColumnDialog, ColumnDialogData } from './column-dialog/column-dialog';
import { ColumnManagement } from './column-management';
import { Column } from './column.models';

/** Column-owned controls and dialogs; the workspace projects read-only task content. */
@Component({
  selector: 'app-column-controls',
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, Icon, IconButton, NgTemplateOutlet],
  templateUrl: './column-controls.html',
  styleUrl: './column-controls.scss',
})
export class ColumnControls implements OnDestroy {
  readonly taskContent =
    contentChild.required<TemplateRef<{ $implicit: WorkspaceColumn }>>(TemplateRef);
  readonly state = inject(BoardWorkspaceState);
  readonly mutations = inject(ColumnManagement);
  readonly reorderError = signal('');
  readonly lanes = computed(() => {
    const state = this.state.workspace();
    return state.status === 'ready' ? state.columns : [];
  });
  private readonly dialogs = inject(TaskflowDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly active = signal<DialogRef<unknown, DialogFrame> | null>(null);

  constructor() {
    effect(() => {
      this.state.generation();
      this.reorderError.set('');
      untracked(() => this.active()?.close());
    });
  }

  ngOnDestroy(): void {
    this.active()?.close();
  }
  openCreate(returnFocus: HTMLElement): void {
    this.open({ kind: 'create', mutations: this.mutations }, returnFocus);
  }
  openRename(column: Column, returnFocus: HTMLElement): void {
    this.open({ kind: 'rename', column, mutations: this.mutations }, returnFocus);
  }
  openDelete(column: Column, returnFocus: HTMLElement): void {
    this.open({ kind: 'delete', column, mutations: this.mutations }, returnFocus);
  }

  async move(id: string, direction: -1 | 1): Promise<void> {
    const generation = this.state.generation();
    this.reorderError.set('');
    const result = await this.mutations.move(id, direction);
    if (generation === this.state.generation() && !result.success && !result.stale)
      this.reorderError.set(result.error);
  }

  private open(data: ColumnDialogData, returnFocus: HTMLElement): void {
    if (this.active()) return;
    const ref = this.dialogs.open(ColumnDialog, {
      title:
        data.kind === 'create'
          ? 'Create column'
          : data.kind === 'rename'
            ? 'Rename column'
            : 'Delete column?',
      size: data.kind === 'delete' ? 'sm' : 'md',
      autoFocus: data.kind === 'delete' ? '[data-column-cancel]' : 'input',
      restoreFocus: returnFocus,
      data,
    });
    this.active.set(ref);
    ref.closed.subscribe(() => {
      if (!this.destroyRef.destroyed) this.active.set(null);
    });
  }
}
