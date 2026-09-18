import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { BoardWorkspaceState } from './board-workspace-state';

export const board = { id: 'one', name: 'Roadmap', createdAt: '', updatedAt: '' };
const columns = ['z', 'a', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: '',
  updatedAt: '',
}));
const tasks = (columnId: string) =>
  ['second', 'first'].map((id, position) => ({
    id: columnId + id,
    columnId,
    title: id,
    description: null,
    priority: 'MEDIUM',
    dueDate: null,
    position,
    createdAt: '',
    updatedAt: '',
  }));

describe('Board workspace state', () => {
  let state: BoardWorkspaceState;
  let http: HttpTestingController;
  let ids: BehaviorSubject<string>;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BoardWorkspaceState,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    state = TestBed.inject(BoardWorkspaceState);
    http = TestBed.inject(HttpTestingController);
    ids = new BehaviorSubject('one');
    state.connect(ids);
  });
  afterEach(() => http.verify());
  function readBoard() {
    http.expectOne('/api/boards/one').flush(board);
  }
  function readColumns() {
    http.expectOne('/api/boards/one/columns').flush(columns);
  }
  function fail(url: string, code = 'INTERNAL_ERROR', status = 500) {
    http
      .expectOne(url)
      .flush({ code, detail: 'Sensitive internal information' }, { status, statusText: 'Failure' });
  }
  it('loads Board then Columns then Board Tasks, preserving canonical server order', () => {
    expect(state.workspace()).toEqual({ status: 'loading' });
    http.expectNone('/api/boards/one/columns');
    readBoard();
    http.expectNone((r) => r.url.includes('/tasks'));
    readColumns();
    expect(state.workspace()).toEqual({ status: 'loading' });
    http.expectOne('/api/boards/one/tasks').flush(columns.flatMap((column) => tasks(column.id)));
    expect(state.workspace()).toEqual({
      status: 'ready',
      board,
      columns: columns.map((column) => ({ column, tasks: tasks(column.id) })),
    });
  });
  it('completes a zero-column workspace without task requests', () => {
    readBoard();
    http.expectOne('/api/boards/one/columns').flush([]);
    expect(state.workspace()).toEqual({ status: 'ready', board, columns: [] });
  });
  it.each(['board', 'columns', 'tasks'])(
    'treats %s BOARD_NOT_FOUND as final not-found',
    (stage) => {
      if (stage !== 'board') readBoard();
      if (stage === 'tasks') readColumns();
      fail('/api/boards/one' + (stage === 'board' ? '' : '/' + stage), 'BOARD_NOT_FOUND', 404);
      expect(state.workspace()).toEqual({ status: 'not-found' });
    },
  );
  it.each(['board', 'columns', 'tasks'])('clears all content on a generic %s failure', (stage) => {
    if (stage !== 'board') readBoard();
    if (stage === 'tasks') {
      readColumns();
      fail('/api/boards/one/tasks');
    } else fail('/api/boards/one' + (stage === 'columns' ? '/columns' : ''));
    expect(state.workspace()).toEqual({ status: 'error' });
  });
  it('rejects unknown Task Columns without publishing partial content and retries the full pipeline', () => {
    readBoard();
    http.expectOne('/api/boards/one/columns').flush([columns[0]]);
    http.expectOne('/api/boards/one/tasks').flush([...tasks('z'), ...tasks('unknown')]);
    expect(state.workspace()).toEqual({ status: 'error' });
    state.retry();
    expect(state.workspace()).toEqual({ status: 'loading' });
    readBoard();
    http.expectOne('/api/boards/one/columns').flush([columns[0]]);
    http.expectOne('/api/boards/one/tasks').flush([]);
    expect(state.workspace()).toEqual({
      status: 'ready',
      board,
      columns: [{ column: columns[0], tasks: [] }],
    });
  });
  it.each(['board', 'columns', 'tasks'])(
    'cancels an in-flight %s stage on route change',
    (stage) => {
      if (stage !== 'board') readBoard();
      if (stage === 'tasks') http.expectOne('/api/boards/one/columns').flush([columns[0]]);
      const old = http.expectOne(
        stage === 'tasks'
          ? '/api/boards/one/tasks'
          : '/api/boards/one' + (stage === 'columns' ? '/columns' : ''),
      );
      ids.next('two');
      expect(old.cancelled).toBe(true);
      expect(state.workspace()).toEqual({ status: 'loading' });
      http.expectOne('/api/boards/two').flush({ ...board, id: 'two' });
      http.expectOne('/api/boards/two/columns').flush([]);
      expect(state.workspace()).toEqual({
        status: 'ready',
        board: { ...board, id: 'two' },
        columns: [],
      });
    },
  );
  it('does not reload a repeated route value and retries the current Board', () => {
    ids.next('one');
    readBoard();
    http.expectOne('/api/boards/one/columns').flush([]);
    ids.next('two');
    fail('/api/boards/two');
    state.retry();
    http.expectOne('/api/boards/two').flush({ ...board, id: 'two' });
    http.expectOne('/api/boards/two/columns').flush([]);
    expect(state.workspace().status).toBe('ready');
  });
  it('cancels pending requests when its page injector is destroyed', () => {
    const pending = http.expectOne('/api/boards/one');
    TestBed.resetTestingModule();
    expect(pending.cancelled).toBe(true);
  });
  it.each([1, 20])(
    'makes exactly one Task-list request for %s Columns on load and retry',
    (count) => {
      const lanes = Array.from({ length: count }, (_, position) => ({
        ...columns[0],
        id: 'c' + position,
        position,
      }));
      for (let attempt = 0; attempt < 2; attempt++) {
        if (attempt) state.retry();
        readBoard();
        http.expectOne('/api/boards/one/columns').flush(lanes);
        const requests = http.match(
          (request) => request.method === 'GET' && request.url.endsWith('/tasks'),
        );
        expect(requests).toHaveLength(1);
        expect(requests[0].request.url).toBe('/api/boards/one/tasks');
        requests[0].flush(lanes.flatMap((lane) => tasks(lane.id)));
        expect(state.workspace()).toEqual({
          status: 'ready',
          board,
          columns: lanes.map((column) => ({ column, tasks: tasks(column.id) })),
        });
      }
    },
  );
});
