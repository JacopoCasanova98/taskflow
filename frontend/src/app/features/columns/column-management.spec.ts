import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardWorkspaceState } from '../boards/board-workspace/board-workspace-state';
import { ColumnManagement } from './column-management';
import { Column } from './column.models';

const columns: Column[] = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: 'old',
  updatedAt: 'old',
}));
describe('Column management', () => {
  let state: BoardWorkspaceState;
  let mutations: ColumnManagement;
  let http: HttpTestingController;
  let ids: BehaviorSubject<string>;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BoardWorkspaceState,
        ColumnManagement,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    state = TestBed.inject(BoardWorkspaceState);
    mutations = TestBed.inject(ColumnManagement);
    http = TestBed.inject(HttpTestingController);
    ids = new BehaviorSubject('one');
    state.connect(ids);
  });
  afterEach(() => http.verify());
  function load(boardId = 'one', response = columns) {
    http
      .expectOne('/api/boards/' + boardId)
      .flush({ id: boardId, name: boardId, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + boardId + '/columns').flush(response);
    if (response.length)
      http.expectOne('/api/boards/' + boardId + '/tasks').flush(
        response.map((column) => ({
          id: 'task-' + column.id,
          columnId: column.id,
          title: 'Read only',
          description: null,
          priority: 'LOW',
          dueDate: null,
          position: 0,
          createdAt: '',
          updatedAt: '',
        })),
      );
  }
  function ready() {
    const value = state.workspace();
    if (value.status !== 'ready') throw new Error('Expected ready');
    return value;
  }
  function fail(url: string, code = 'INTERNAL_ERROR', status = 500) {
    http.expectOne(url).flush({ code, detail: 'Secret' }, { status, statusText: 'Failure' });
  }
  it.each([true, false])('appends the canonical created Column, empty Board: %s', async (empty) => {
    load('one', empty ? [] : columns);
    const previous = ready().columns;
    const result = mutations.create('New');
    expect(ready().columns).toBe(previous);
    const canonical = {
      ...columns[0],
      id: 'new',
      name: 'Canonical',
      position: previous.length,
      updatedAt: 'new',
    };
    http.expectOne('/api/boards/one/columns').flush(canonical);
    expect(await result).toEqual({ success: true });
    expect(ready().columns).toEqual([...previous, { column: canonical, tasks: [] }]);
    previous.forEach((lane, index) => expect(ready().columns[index]).toBe(lane));
  });
  it('renames metadata only, keeping lane order and Task array identity', async () => {
    load();
    const before = ready().columns;
    const result = mutations.rename('b', 'Renamed');
    const canonical = { ...columns[1], name: 'Canonical', updatedAt: 'new' };
    http.expectOne('/api/columns/b').flush(canonical);
    await result;
    expect(ready().columns.map((lane) => lane.column)).toEqual([columns[0], canonical, columns[2]]);
    expect(ready().columns[1].tasks).toBe(before[1].tasks);
  });
  it('removes only the confirmed deleted Column and compacts survivor positions', async () => {
    load();
    const before = ready().columns;
    const result = mutations.delete('b');
    expect(ready().columns).toBe(before);
    http.expectOne('/api/columns/b').flush(null, { status: 204, statusText: 'No Content' });
    await result;
    expect(ready().columns.map((lane) => [lane.column.id, lane.column.position])).toEqual([
      ['a', 0],
      ['c', 1],
    ]);
    expect(ready().columns[0].tasks).toBe(before[0].tasks);
    expect(ready().columns[1].tasks).toBe(before[2].tasks);
  });
  it('submits complete order pessimistically and uses canonical response with Tasks by id', async () => {
    load();
    const before = ready().columns;
    const result = mutations.move('b', -1);
    const request = http.expectOne('/api/boards/one/columns/order');
    expect(request.request.body).toEqual({ columnIds: ['b', 'a', 'c'] });
    expect(ready().columns).toBe(before);
    await mutations.move('b', -1);
    http.expectNone('/api/boards/one/columns/order');
    const canonical = [columns[1], columns[0], columns[2]].map((column, position) => ({
      ...column,
      position,
      updatedAt: 'new',
    }));
    request.flush(canonical);
    await result;
    expect(ready().columns.map((lane) => lane.column)).toEqual(canonical);
    expect(ready().columns.map((lane) => lane.tasks)).toEqual([
      before[1].tasks,
      before[0].tasks,
      before[2].tasks,
    ]);
    expect(ready().columns[0].tasks).toBe(before[1].tasks);
  });
  it.each(['create', 'rename', 'delete', 'reorder'] as const)(
    'preserves ready state on generic %s failure',
    async (operation) => {
      load();
      const before = state.workspace();
      const promise =
        operation === 'create'
          ? mutations.create('New')
          : operation === 'rename'
            ? mutations.rename('b', 'New')
            : operation === 'delete'
              ? mutations.delete('b')
              : mutations.move('b', -1);
      fail(
        operation === 'create'
          ? '/api/boards/one/columns'
          : operation === 'reorder'
            ? '/api/boards/one/columns/order'
            : '/api/columns/b',
      );
      expect(await promise).toEqual({
        success: false,
        error:
          "We couldn't " +
          operation +
          (operation === 'reorder' ? ' the columns.' : ' the column.') +
          ' Please try again.',
      });
      expect(state.workspace()).toBe(before);
      expect(mutations.busy()).toBe(false);
    },
  );
  it('retains non-empty Column, Tasks, and ordering after backend refusal', async () => {
    load();
    const before = state.workspace();
    const result = mutations.delete('b');
    fail('/api/columns/b', 'COLUMN_NOT_EMPTY', 409);
    expect(await result).toEqual({
      success: false,
      error: 'This column must be empty before it can be deleted.',
    });
    expect(state.workspace()).toBe(before);
  });
  it.each(['rename', 'delete'] as const)(
    'reconciles stale %s with the full loader',
    async (operation) => {
      load();
      const result = operation === 'rename' ? mutations.rename('b', 'New') : mutations.delete('b');
      fail('/api/columns/b', 'COLUMN_NOT_FOUND', 404);
      await result;
      expect(state.workspace().status).toBe('loading');
      load('one', [columns[0]]);
      expect(ready().columns.length).toBe(1);
    },
  );
  it('announces an order conflict and reloads without retrying the order', async () => {
    load();
    const result = mutations.move('b', -1);
    fail('/api/boards/one/columns/order', 'COLUMN_ORDER_CONFLICT', 409);
    await result;
    expect(mutations.notice()).toBe('Columns changed on the server. Reloading the board.');
    load();
    expect(mutations.notice()).toBe('');
  });
  it.each(['create', 'reorder'] as const)(
    'handles BOARD_NOT_FOUND during %s without a reload',
    async (operation) => {
      load();
      const result = operation === 'create' ? mutations.create('New') : mutations.move('b', -1);
      fail(
        '/api/boards/one/columns' + (operation === 'reorder' ? '/order' : ''),
        'BOARD_NOT_FOUND',
        404,
      );
      await result;
      expect(state.workspace()).toEqual({ status: 'not-found' });
    },
  );
  it.each(['missing', 'unknown', 'duplicate'])(
    'reloads unexpected reorder response membership: %s',
    async (kind) => {
      load();
      const result = mutations.move('b', -1);
      const response =
        kind === 'missing'
          ? columns.slice(1)
          : kind === 'unknown'
            ? [columns[0], columns[1], { ...columns[2], id: 'unknown' }]
            : [columns[0], columns[0], columns[2]];
      http.expectOne('/api/boards/one/columns/order').flush(response);
      await result;
      expect(state.workspace().status).toBe('loading');
      load();
    },
  );
  it.each(['create', 'rename', 'delete', 'reorder'] as const)(
    'ignores late %s success after navigating to another Board',
    async (operation) => {
      load();
      const result =
        operation === 'create'
          ? mutations.create('New')
          : operation === 'rename'
            ? mutations.rename('b', 'New')
            : operation === 'delete'
              ? mutations.delete('b')
              : mutations.move('b', -1);
      const request = http.expectOne(
        operation === 'create'
          ? '/api/boards/one/columns'
          : operation === 'reorder'
            ? '/api/boards/one/columns/order'
            : '/api/columns/b',
      );
      ids.next('two');
      load('two');
      const before = state.workspace();
      request.flush(
        operation === 'delete'
          ? null
          : operation === 'reorder'
            ? columns
            : { ...columns[0], id: 'stale' },
      );
      expect((await result).success).toBe(false);
      expect(state.workspace()).toBe(before);
      expect(mutations.busy()).toBe(false);
    },
  );
  it.each(['create', 'rename', 'delete', 'reorder'] as const)(
    'ignores late %s errors on another Board',
    async (operation) => {
      load();
      const result =
        operation === 'create'
          ? mutations.create('New')
          : operation === 'rename'
            ? mutations.rename('b', 'New')
            : operation === 'delete'
              ? mutations.delete('b')
              : mutations.move('b', -1);
      const request = http.expectOne(
        operation === 'create'
          ? '/api/boards/one/columns'
          : operation === 'reorder'
            ? '/api/boards/one/columns/order'
            : '/api/columns/b',
      );
      ids.next('two');
      load('two');
      const before = state.workspace();
      request.flush({ code: 'BOARD_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
      await result;
      expect(state.workspace()).toBe(before);
      expect(mutations.notice()).toBe('');
    },
  );
  it('ignores a response after reload of the same Board and does not clear a newer pending write', async () => {
    load();
    const old = mutations.create('Old');
    const request = http.expectOne('/api/boards/one/columns');
    state.retry();
    load();
    const newer = mutations.create('New');
    const newerRequest = http.expectOne('/api/boards/one/columns');
    request.flush({ ...columns[0], id: 'old' });
    await old;
    expect(mutations.busy()).toBe(true);
    newerRequest.flush({ ...columns[0], id: 'new' });
    await newer;
    expect(ready().columns.map((lane) => lane.column.id)).toEqual(['a', 'b', 'c', 'new']);
  });
  it('blocks boundary moves and overlapping writes', async () => {
    load();
    await mutations.move('a', -1);
    await mutations.move('c', 1);
    const result = mutations.move('b', -1);
    const request = http.expectOne('/api/boards/one/columns/order');
    await mutations.create('New');
    await mutations.rename('a', 'New');
    await mutations.delete('a');
    request.flush(columns);
    await result;
  });
});
