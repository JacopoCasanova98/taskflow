import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { isHttpProblem } from '../../../core/http/http-problem';
import { BoardApi } from '../board-api';
import { Board } from '../board.models';

export type BoardMutationResult =
  { success: true } | { success: false; error: string; stale?: boolean };

/** Page-scoped state: never retained across authenticated page activations. */
@Injectable()
export class BoardListState {
  private readonly api = inject(BoardApi);
  private readonly boardState = signal<readonly Board[]>([]);
  private readonly loadingState = signal(true);
  private readonly errorState = signal<string | null>(null);
  private readonly noticeState = signal<string | null>(null);
  private loadSequence = 0;
  readonly boards = this.boardState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  readonly loadError = this.errorState.asReadonly();
  readonly notice = this.noticeState.asReadonly();

  async load(): Promise<void> {
    const sequence = ++this.loadSequence;
    this.loadingState.set(true);
    this.errorState.set(null);
    try {
      const boards = await firstValueFrom(this.api.listBoards());
      if (sequence === this.loadSequence) this.boardState.set(boards);
    } catch {
      // Retain confirmed data, but show the error surface until retry succeeds.
      if (sequence === this.loadSequence) this.errorState.set("We couldn't load your boards.");
    } finally {
      if (sequence === this.loadSequence) this.loadingState.set(false);
    }
  }

  async create(name: string): Promise<BoardMutationResult> {
    try {
      const board = await firstValueFrom(this.api.createBoard({ name }));
      this.boardState.update((boards) => [board, ...boards]);
      this.refreshPendingLoad();
      return { success: true };
    } catch (error) {
      return this.failure(error, 'create');
    }
  }

  async rename(boardId: string, name: string): Promise<BoardMutationResult> {
    try {
      const board = await firstValueFrom(this.api.renameBoard(boardId, { name }));
      this.boardState.update((boards) =>
        boards.map((item) => (item.id === boardId ? board : item)),
      );
      this.refreshPendingLoad();
      return { success: true };
    } catch (error) {
      return this.failure(error, 'rename');
    }
  }

  async delete(boardId: string): Promise<BoardMutationResult> {
    try {
      await firstValueFrom(this.api.deleteBoard(boardId));
      this.boardState.update((boards) => boards.filter((board) => board.id !== boardId));
      this.refreshPendingLoad();
      return { success: true };
    } catch (error) {
      return this.failure(error, 'delete');
    }
  }

  private refreshPendingLoad(): void {
    // A GET begun before a confirmed mutation must not overwrite that mutation.
    if (this.loading()) void this.load();
  }

  private failure(error: unknown, action: 'create' | 'rename' | 'delete'): BoardMutationResult {
    if (action !== 'create' && isHttpProblem(error, 404, 'BOARD_NOT_FOUND')) {
      const message = 'This board is no longer available.';
      this.noticeState.set(message);
      void this.load();
      return { success: false, error: message, stale: true };
    }
    return {
      success: false,
      error:
        action !== 'delete' && isHttpProblem(error, 400, 'VALIDATION_FAILED')
          ? 'Enter a non-blank board name of no more than 120 characters.'
          : "We couldn't " + action + ' the board. Please try again.',
    };
  }
}
