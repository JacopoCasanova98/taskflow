import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { isHttpProblem } from '../../core/http/http-problem';
import {
  BoardWorkspaceState,
  WorkspaceColumn,
} from '../boards/board-workspace/board-workspace-state';
import { ColumnApi } from './column-api';

export type ColumnMutationResult =
  { success: true } | { success: false; error: string; stale?: boolean };
type Operation = 'create' | 'rename' | 'delete' | 'reorder';

/** Page-scoped Column writes. Tasks are retained by identity, never mutated. */
@Injectable()
export class ColumnManagement {
  private readonly workspace = inject(BoardWorkspaceState);
  private readonly api = inject(ColumnApi);
  private readonly feedback = signal<{ generation: number; message: string } | null>(null);
  readonly busy = this.workspace.writing;
  readonly notice = computed(() => {
    const feedback = this.feedback();
    return feedback?.generation === this.workspace.generation() &&
      this.workspace.workspace().status === 'loading'
      ? feedback.message
      : '';
  });

  create(name: string): Promise<ColumnMutationResult> {
    const current = this.workspace.workspace();
    if (current.status !== 'ready') return Promise.resolve(this.stale());
    return this.mutate(
      'create',
      () => this.api.createColumn(current.board.id, { name }),
      (column, generation) =>
        this.workspace.updateColumns(generation, (lanes) => [...lanes, { column, tasks: [] }]),
    );
  }

  rename(id: string, name: string): Promise<ColumnMutationResult> {
    return this.mutate(
      'rename',
      () => this.api.renameColumn(id, { name }),
      (column, generation) =>
        this.workspace.updateColumns(generation, (lanes) =>
          lanes.map((lane) => (lane.column.id === id ? { ...lane, column } : lane)),
        ),
    );
  }

  delete(id: string): Promise<ColumnMutationResult> {
    return this.mutate(
      'delete',
      () => this.api.deleteColumn(id),
      (_, generation) =>
        this.workspace.updateColumns(generation, (lanes) =>
          lanes
            .filter((lane) => lane.column.id !== id)
            .map((lane, position) => ({ ...lane, column: { ...lane.column, position } })),
        ),
    );
  }

  move(id: string, direction: -1 | 1): Promise<ColumnMutationResult> {
    const current = this.workspace.workspace();
    if (current.status !== 'ready') return Promise.resolve(this.stale());
    const ids = current.columns.map((lane) => lane.column.id);
    const index = ids.indexOf(id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= ids.length) return Promise.resolve(this.stale());
    [ids[index], ids[target]] = [ids[target], ids[index]];
    return this.mutate(
      'reorder',
      () => this.api.reorderColumns(current.board.id, { columnIds: ids }),
      (columns, generation) => {
        const existing = new Map(current.columns.map((lane) => [lane.column.id, lane]));
        if (
          columns.length !== existing.size ||
          new Set(columns.map((c) => c.id)).size !== existing.size ||
          columns.some((column) => !existing.has(column.id))
        ) {
          this.reconcile('Columns changed on the server. Reloading the board.');
          return;
        }
        this.workspace.updateColumns(generation, () =>
          columns.map((column): WorkspaceColumn => ({
            column,
            tasks: existing.get(column.id)!.tasks,
          })),
        );
      },
    );
  }

  private async mutate<T>(
    operation: Operation,
    request: () => Observable<T>,
    apply: (response: T, generation: number) => void,
  ): Promise<ColumnMutationResult> {
    const pending = this.workspace.beginWrite();
    if (!pending) return this.stale();
    const { generation } = pending;
    this.feedback.set(null);
    try {
      const response = await firstValueFrom(request());
      if (generation !== this.workspace.generation()) return this.stale();
      apply(response, generation);
      return { success: true };
    } catch (error: unknown) {
      if (generation !== this.workspace.generation()) return this.stale();
      if (isHttpProblem(error, 404, 'BOARD_NOT_FOUND')) {
        this.workspace.boardNotFound();
        return this.stale();
      }
      if (isHttpProblem(error, 404, 'COLUMN_NOT_FOUND')) {
        this.reconcile('Columns changed on the server. Reloading the board.');
        return this.stale();
      }
      if (operation === 'reorder' && isHttpProblem(error, 409, 'COLUMN_ORDER_CONFLICT')) {
        this.reconcile('Columns changed on the server. Reloading the board.');
        return this.stale();
      }
      const message =
        operation === 'delete' && isHttpProblem(error, 409, 'COLUMN_NOT_EMPTY')
          ? 'This column must be empty before it can be deleted.'
          : (operation === 'create' || operation === 'rename') &&
              isHttpProblem(error, 400, 'VALIDATION_FAILED')
            ? 'Enter a non-blank column name of no more than 120 characters.'
            : "We couldn't " +
              operation +
              (operation === 'reorder' ? ' the columns.' : ' the column.') +
              ' Please try again.';
      return { success: false, error: message };
    } finally {
      this.workspace.endWrite(pending);
    }
  }

  private reconcile(message: string): void {
    this.workspace.retry();
    this.feedback.set({ generation: this.workspace.generation(), message });
  }

  private stale(): ColumnMutationResult {
    return { success: false, stale: true, error: '' };
  }
}
