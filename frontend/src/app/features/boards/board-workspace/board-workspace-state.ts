import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  catchError,
  distinctUntilChanged,
  forkJoin,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  defer,
} from 'rxjs';
import { isHttpProblem } from '../../../core/http/http-problem';
import { ColumnApi } from '../../columns/column-api';
import { Column } from '../../columns/column.models';
import { TaskApi } from '../../tasks/task-api';
import { Task } from '../../tasks/task.models';
import { BoardApi } from '../board-api';
import { Board } from '../board.models';

export interface WorkspaceColumn {
  readonly column: Column;
  readonly tasks: readonly Task[];
}
export type WorkspaceState =
  | { readonly status: 'loading' | 'not-found' | 'error' }
  | {
      readonly status: 'ready';
      readonly board: Board;
      readonly columns: readonly WorkspaceColumn[];
    };

/** Owned by one page; only a complete server snapshot becomes ready. */
@Injectable()
export class BoardWorkspaceState {
  private readonly boards = inject(BoardApi);
  private readonly columns = inject(ColumnApi);
  private readonly tasks = inject(TaskApi);
  private readonly value = signal<WorkspaceState>({ status: 'loading' });
  private readonly retries = new Subject<void>();
  private readonly untilDestroyed = takeUntilDestroyed<WorkspaceState>();
  private readonly generationState = signal(0);
  readonly generation = this.generationState.asReadonly();
  readonly workspace = this.value.asReadonly();
  private readonly statisticsVersion = signal(0);
  readonly statisticsRevision = this.statisticsVersion.asReadonly();

  /** Invalidate only after a confirmed mutation in this ready generation. */
  mutationConfirmed(generation: number): void {
    if (generation === this.generation() && this.workspace().status === 'ready')
      this.statisticsVersion.update((value) => value + 1);
  }

  private readonly pendingWrite = signal<{ readonly generation: number } | null>(null);
  readonly writing = computed(() => this.pendingWrite()?.generation === this.generation());

  /** One Column or Task write at a time in this workspace generation. */
  beginWrite(): { readonly generation: number } | null {
    if (this.writing() || this.workspace().status !== 'ready') return null;
    const write = { generation: this.generation() };
    this.pendingWrite.set(write);
    return write;
  }

  endWrite(write: { readonly generation: number }): void {
    if (this.pendingWrite() === write) this.pendingWrite.set(null);
  }

  constructor() {
    inject(DestroyRef).onDestroy(() => this.generationState.update((value) => value + 1));
  }

  /** Apply a controlled mutation only to the still-current ready workspace. */
  updateColumns(
    generation: number,
    update: (columns: readonly WorkspaceColumn[]) => readonly WorkspaceColumn[],
  ): void {
    const current = this.workspace();
    if (this.generation() === generation && current.status === 'ready') {
      this.value.set({ ...current, columns: update(current.columns) });
    }
  }

  boardNotFound(): void {
    this.generationState.update((value) => value + 1);
    this.value.set({ status: 'not-found' });
  }

  connect(boardIds: Observable<string>): void {
    boardIds
      .pipe(
        distinctUntilChanged(),
        switchMap((id) =>
          this.retries.pipe(
            startWith(undefined),
            switchMap(() =>
              defer(() => {
                this.generationState.update((value) => value + 1);
                return this.load(id).pipe(startWith({ status: 'loading' } as WorkspaceState));
              }),
            ),
          ),
        ),
        this.untilDestroyed,
      )
      .subscribe((state) => this.value.set(state));
  }

  retry(): void {
    this.retries.next();
  }

  private load(id: string): Observable<WorkspaceState> {
    return this.boards.getBoard(id).pipe(
      switchMap((board) =>
        this.columns.listColumns(id).pipe(
          switchMap((columns) =>
            columns.length === 0
              ? of([])
              : forkJoin(
                  columns.map((column) =>
                    this.tasks.listTasks(column.id).pipe(map((tasks) => ({ column, tasks }))),
                  ),
                ),
          ),
          map((columns): WorkspaceState => ({ status: 'ready', board, columns })),
        ),
      ),
      catchError((error: unknown) =>
        of<WorkspaceState>({
          status: isHttpProblem(error, 404, 'BOARD_NOT_FOUND') ? 'not-found' : 'error',
        }),
      ),
    );
  }
}
