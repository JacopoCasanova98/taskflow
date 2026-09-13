import { computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, of, switchMap, timer } from 'rxjs';
import { isHttpProblem } from '../../core/http/http-problem';
import { BoardWorkspaceState } from '../boards/board-workspace/board-workspace-state';
import { TaskApi } from './task-api';

/** Owned by TaskView on one Board page. Search responses retain IDs only. */
export class TaskSearch {
  private readonly api = inject(TaskApi);
  readonly input = signal('');
  readonly query = computed(() => this.input().trim());
  readonly active = computed(() => this.query() !== '');
  readonly status = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  private readonly ids = signal<ReadonlySet<string> | null>(null);
  readonly membership = this.ids.asReadonly();
  readonly empty = computed(
    () => this.active() && this.status() === 'ready' && this.ids()?.size === 0,
  );
  private readonly requests = new Subject<{ delay: number }>();
  private workspace?: BoardWorkspaceState;
  private revision = 0;
  private reconciled = false;

  constructor() {
    this.requests
      .pipe(
        switchMap(({ delay }) => {
          const state = this.workspace?.workspace();
          const query = this.query();
          const revision = ++this.revision;
          const generation = this.workspace?.generation();
          if (delay < 0 || !query || state?.status !== 'ready') return EMPTY;
          if (query.length > 200) {
            this.status.set('error');
            return EMPTY;
          }
          return (delay ? timer(delay) : of(0)).pipe(
            switchMap(() => {
              this.status.set('loading');
              return this.api
                .searchBoardTasks(state.board.id, query)
                .pipe(catchError((error: unknown) => of({ error })));
            }),
            switchMap((result) => {
              if (revision !== this.revision || generation !== this.workspace?.generation())
                return EMPTY;
              if (!Array.isArray(result)) {
                if (isHttpProblem(result.error, 404, 'BOARD_NOT_FOUND'))
                  this.workspace!.boardNotFound();
                else this.status.set('error');
                return EMPTY;
              }
              const current = this.workspace!.workspace();
              if (current.status !== 'ready') return EMPTY;
              const known = new Set(
                current.columns.flatMap((lane) => lane.tasks.map((task) => task.id)),
              );
              if (result.some((task) => !known.has(task.id))) {
                if (!this.reconciled) {
                  this.reconciled = true;
                  this.workspace!.retry();
                } else this.status.set('error');
                return EMPTY;
              }
              this.ids.set(new Set(result.map((task) => task.id)));
              this.status.set('ready');
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  connect(workspace: BoardWorkspaceState): void {
    this.workspace = workspace;
    let previousGeneration = -1;
    let wasReady = false;
    effect(() => {
      const generation = workspace.generation();
      const ready = workspace.workspace().status === 'ready';
      untracked(() => {
        if (generation !== previousGeneration || !ready) {
          this.requests.next({ delay: -1 });
          this.ids.set(null);
          this.status.set('idle');
        }
        if (ready && (!wasReady || generation !== previousGeneration)) this.refresh(false);
        previousGeneration = generation;
        wasReady = ready;
      });
    });
  }

  setInput(value: string): void {
    const previous = this.query();
    this.input.set(value);
    if (previous === this.query()) return;
    this.reconciled = false;
    this.status.set('idle');
    if (!this.active()) {
      this.ids.set(null);
      this.status.set('idle');
    }
    this.requests.next({ delay: 300 });
  }
  clear(): void {
    this.setInput('');
  }
  refresh(resetReconciliation = true): void {
    if (resetReconciliation) this.reconciled = false;
    this.requests.next({ delay: 0 });
  }
}
