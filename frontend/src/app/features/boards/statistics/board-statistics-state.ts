import { computed, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { isHttpProblem } from '../../../core/http/http-problem';
import { TaskLocalDay } from '../../tasks/task-local-day';
import { BoardWorkspaceState } from '../board-workspace/board-workspace-state';
import { BoardStatisticsApi } from './board-statistics-api';
import { BoardStatistics } from './board-statistics.models';

export type StatisticsState =
  | { readonly status: 'loading' | 'error' }
  | { readonly status: 'ready'; readonly data: BoardStatistics };

/** Server projection owned by the Board page, independent of Task view controls. */
@Injectable()
export class BoardStatisticsState {
  private readonly workspace = inject(BoardWorkspaceState);
  private readonly day = inject(TaskLocalDay);
  private readonly api = inject(BoardStatisticsApi);
  private readonly retries = signal(0);
  private readonly request = computed(() => {
    const workspace = this.workspace.workspace();
    return workspace.status === 'ready'
      ? JSON.stringify([
          workspace.board.id,
          this.day.today(),
          this.workspace.generation(),
          this.workspace.statisticsRevision(),
          this.retries(),
        ])
      : null;
  });
  private readonly result = signal<{ key: string; state: StatisticsState } | null>(null);
  readonly state = computed<StatisticsState>(() => {
    const result = this.result();
    return result?.key === this.request() ? result!.state : { status: 'loading' };
  });

  constructor() {
    effect((onCleanup) => {
      const key = this.request();
      if (key === null) return;
      const [boardId, asOf] = JSON.parse(key) as [string, string];
      untracked(() => {
        this.result.set({ key, state: { status: 'loading' } });
        const subscription = this.api.getStatistics(boardId, asOf).subscribe({
          next: (data) => {
            if (key === this.request()) this.result.set({ key, state: { status: 'ready', data } });
          },
          error: (error: unknown) => {
            if (key !== this.request()) return;
            if (isHttpProblem(error, 404, 'BOARD_NOT_FOUND')) this.workspace.boardNotFound();
            else this.result.set({ key, state: { status: 'error' } });
          },
        });
        onCleanup(() => subscription.unsubscribe());
      });
    });
  }

  retry(): void {
    this.retries.update((value) => value + 1);
  }
}
