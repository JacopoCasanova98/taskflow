import {
  Component,
  computed,
  contentChild,
  effect,
  inject,
  signal,
  TemplateRef,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import {
  BoardWorkspaceState,
  WorkspaceColumn,
} from '../boards/board-workspace/board-workspace-state';
import { ColumnNameForm } from './column-name-form/column-name-form';
import { ColumnManagement } from './column-management';
import { Column } from './column.models';

/** Column-owned editing controls; the workspace projects its read-only task content. */
@Component({
  selector: 'app-column-controls',
  imports: [ColumnNameForm, NgTemplateOutlet],
  templateUrl: './column-controls.html',
  styleUrl: './column-controls.scss',
})
export class ColumnControls {
  readonly taskContent =
    contentChild.required<TemplateRef<{ $implicit: WorkspaceColumn }>>(TemplateRef);
  readonly state = inject(BoardWorkspaceState);
  readonly mutations = inject(ColumnManagement);
  readonly creating = signal(false);
  readonly editing = signal<Column | null>(null);
  readonly confirming = signal<Column | null>(null);
  readonly deleteError = signal('');
  readonly reorderError = signal('');
  readonly lanes = computed(() => {
    const state = this.state.workspace();
    return state.status === 'ready' ? state.columns : [];
  });
  readonly create = (name: string) => this.mutations.create(name);
  readonly rename = (name: string) => this.mutations.rename(this.editing()!.id, name);

  constructor() {
    effect(() => {
      this.state.generation();
      this.creating.set(false);
      this.editing.set(null);
      this.confirming.set(null);
      this.deleteError.set('');
      this.reorderError.set('');
    });
  }

  confirm(column: Column): void {
    this.deleteError.set('');
    this.confirming.set(column);
  }

  async deleteColumn(): Promise<void> {
    const column = this.confirming();
    if (!column || this.mutations.busy()) return;
    const generation = this.state.generation();
    this.deleteError.set('');
    const result = await this.mutations.delete(column.id);
    if (generation !== this.state.generation()) return;
    if (result.success || result.stale) this.confirming.set(null);
    else this.deleteError.set(result.error);
  }

  async move(id: string, direction: -1 | 1): Promise<void> {
    const generation = this.state.generation();
    this.reorderError.set('');
    const result = await this.mutations.move(id, direction);
    if (generation === this.state.generation() && !result.success && !result.stale) {
      this.reorderError.set(result.error);
    }
  }
}
