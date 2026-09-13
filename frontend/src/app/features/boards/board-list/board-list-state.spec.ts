import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { BoardApi } from '../board-api';
import { Board } from '../board.models';
import { BoardListState } from './board-list-state';

describe('BoardListState', () => {
  const first: Board = {
    id: 'one',
    name: 'Zulu',
    createdAt: '2026-09-11T10:00:00Z',
    updatedAt: '2026-09-11T10:00:00Z',
  };
  const second: Board = { ...first, id: 'two', name: 'Alpha' };
  let state: BoardListState;
  let api: {
    listBoards: ReturnType<typeof vi.fn>;
    createBoard: ReturnType<typeof vi.fn>;
    renameBoard: ReturnType<typeof vi.fn>;
    deleteBoard: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    api = {
      listBoards: vi.fn().mockReturnValue(of([first, second])),
      createBoard: vi.fn(),
      renameBoard: vi.fn(),
      deleteBoard: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [BoardListState, { provide: BoardApi, useValue: api }],
    });
    state = TestBed.inject(BoardListState);
  });
  it('starts loading, without implying a successful empty result', () => {
    expect(state.loading()).toBe(true);
    expect(state.boards()).toEqual([]);
    expect(state.loadError()).toBeNull();
    expect('set' in state.boards).toBe(false);
  });
  it('replaces the list exactly in server order after loading', async () => {
    const pending = new Subject<Board[]>();
    api.listBoards.mockReturnValue(pending);
    const load = state.load();
    expect(state.loading()).toBe(true);
    pending.next([first, second]);
    await load;
    expect(state.boards()).toEqual([first, second]);
    expect(state.loading()).toBe(false);
    expect(state.loadError()).toBeNull();
    api.listBoards.mockReturnValue(of([]));
    await state.load();
    expect(state.boards()).toEqual([]);
  });
  it('retains confirmed data on load failure and clears safe error on retry', async () => {
    await state.load();
    api.listBoards.mockReturnValue(throwError(() => new Error('private detail')));
    await state.load();
    expect(state.boards()).toEqual([first, second]);
    expect(state.loading()).toBe(false);
    expect(state.loadError()).toBe("We couldn't load your boards.");
    api.listBoards.mockReturnValue(of([second]));
    await state.load();
    expect(api.listBoards).toHaveBeenCalledTimes(3);
    expect(state.boards()).toEqual([second]);
    expect(state.loadError()).toBeNull();
  });
  it('ignores an older load completing after a newer response', async () => {
    const older = new Subject<Board[]>();
    api.listBoards.mockReturnValueOnce(older);
    const oldLoad = state.load();
    await state.load();
    older.next([]);
    await oldLoad;
    expect(state.boards()).toEqual([first, second]);
  });
  it('prepends the canonical created Board without another GET', async () => {
    await state.load();
    const created = { ...first, id: 'new', name: 'Canonical' };
    api.createBoard.mockReturnValue(of(created));
    expect(await state.create('Submitted')).toEqual({ success: true });
    expect(api.createBoard).toHaveBeenCalledExactlyOnceWith({ name: 'Submitted' });
    expect(state.boards()).toEqual([created, first, second]);
    expect(api.listBoards).toHaveBeenCalledTimes(1);
  });
  it('replaces exactly the renamed Board including audit data without reordering or GET', async () => {
    await state.load();
    const renamed = { ...second, name: 'Renamed', updatedAt: '2026-09-11T12:00:00Z' };
    api.renameBoard.mockReturnValue(of(renamed));
    await state.rename(second.id, 'Renamed');
    expect(api.renameBoard).toHaveBeenCalledExactlyOnceWith(second.id, { name: 'Renamed' });
    expect(state.boards()).toEqual([first, renamed]);
    expect(api.listBoards).toHaveBeenCalledTimes(1);
  });
  it('removes only the confirmed deleted Board without another GET', async () => {
    await state.load();
    const pending = new Subject<void>();
    api.deleteBoard.mockReturnValue(pending);
    const deletion = state.delete(first.id);
    expect(state.boards()).toEqual([first, second]);
    pending.next(undefined);
    expect(await deletion).toEqual({ success: true });
    expect(state.boards()).toEqual([second]);
    expect(api.listBoards).toHaveBeenCalledTimes(1);
  });
  for (const action of ['create', 'rename', 'delete'] as const) {
    const method = { create: 'createBoard', rename: 'renameBoard', delete: 'deleteBoard' }[
      action
    ] as 'createBoard' | 'renameBoard' | 'deleteBoard';
    it.each([0, 400, 404, 500])(
      'preserves state for ' + action + ' failure %s and hides server details',
      async (status) => {
        await state.load();
        api[method].mockReturnValue(
          throwError(
            () => new HttpErrorResponse({ status, error: { code: 'UNKNOWN', detail: 'secret' } }),
          ),
        );
        const result =
          action === 'create'
            ? await state.create('Name')
            : action === 'rename'
              ? await state.rename(first.id, 'Name')
              : await state.delete(first.id);
        expect(result).toEqual({
          success: false,
          error: "We couldn't " + action + ' the board. Please try again.',
        });
        expect(state.boards()).toEqual([first, second]);
        expect(api.listBoards).toHaveBeenCalledTimes(1);
      },
    );
  }
  it.each(['create', 'rename'] as const)(
    'maps known %s validation to fixed safe feedback',
    async (action) => {
      await state.load();
      api[action === 'create' ? 'createBoard' : 'renameBoard'].mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { code: 'VALIDATION_FAILED', detail: 'secret' },
            }),
        ),
      );
      const result =
        action === 'create' ? await state.create('Name') : await state.rename(first.id, 'Name');
      expect(result).toEqual({
        success: false,
        error: 'Enter a non-blank board name of no more than 120 characters.',
      });
    },
  );
  it.each(['rename', 'delete'] as const)(
    'reloads on stale %s, preserving server authority',
    async (action) => {
      await state.load();
      const reload = new Subject<Board[]>();
      api.listBoards.mockReturnValue(reload);
      api[action === 'rename' ? 'renameBoard' : 'deleteBoard'].mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 404, error: { code: 'BOARD_NOT_FOUND' } }),
        ),
      );
      const result =
        action === 'rename' ? await state.rename(first.id, 'Name') : await state.delete(first.id);
      expect(result).toEqual({
        success: false,
        stale: true,
        error: 'This board is no longer available.',
      });
      expect(state.notice()).toBe('This board is no longer available.');
      expect(state.boards()).toEqual([first, second]);
      expect(state.loading()).toBe(true);
      expect(api.listBoards).toHaveBeenCalledTimes(2);
      reload.next([second]);
      await Promise.resolve();
      expect(state.boards()).toEqual([second]);
    },
  );
  it('keeps retry available when stale reconciliation fails', async () => {
    await state.load();
    api.listBoards.mockReturnValue(throwError(() => new Error('offline')));
    api.deleteBoard.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { code: 'BOARD_NOT_FOUND' } })),
    );
    await state.delete(first.id);
    expect(state.loadError()).toBe("We couldn't load your boards.");
    expect(state.notice()).toBe('This board is no longer available.');
    expect(state.boards()).toEqual([first, second]);
  });
  it('refreshes an in-flight list after mutation and ignores its older snapshot', async () => {
    await state.load();
    const older = new Subject<Board[]>();
    const created = { ...first, id: 'new' };
    api.listBoards.mockReturnValueOnce(older).mockReturnValue(of([created, first, second]));
    const oldLoad = state.load();
    api.createBoard.mockReturnValue(of(created));
    await state.create('Name');
    older.next([first, second]);
    await oldLoad;
    expect(state.boards()).toEqual([created, first, second]);
    expect(api.listBoards).toHaveBeenCalledTimes(3);
  });
});
