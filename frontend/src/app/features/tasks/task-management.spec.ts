import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardWorkspaceState } from '../boards/board-workspace/board-workspace-state';
import { ColumnManagement } from '../columns/column-management';
import { TaskManagement } from './task-management';
import { Task, UpdateTaskRequest } from './task.models';

const content: UpdateTaskRequest = {
  title: 'New',
  description: null,
  priority: 'MEDIUM',
  dueDate: null,
};
const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: '',
  updatedAt: '',
}));
const tasks = (columnId: string): Task[] =>
  [0, 1, 2].map((position) => ({
    ...content,
    id: columnId + position,
    columnId,
    position,
    createdAt: 'old',
    updatedAt: 'old',
  }));
describe('Task management', () => {
  let state: BoardWorkspaceState;
  let management: TaskManagement;
  let columnManagement: ColumnManagement;
  let http: HttpTestingController;
  let ids: BehaviorSubject<string>;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BoardWorkspaceState,
        TaskManagement,
        ColumnManagement,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    state = TestBed.inject(BoardWorkspaceState);
    management = TestBed.inject(TaskManagement);
    columnManagement = TestBed.inject(ColumnManagement);
    http = TestBed.inject(HttpTestingController);
    ids = new BehaviorSubject('one');
    state.connect(ids);
    load();
    TestBed.tick();
  });
  afterEach(() => http.verify());
  function load(id = 'one') {
    http.expectOne('/api/boards/' + id).flush({ id, name: id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(columns);
    columns.forEach((column) =>
      http.expectOne('/api/columns/' + column.id + '/tasks').flush(tasks(column.id)),
    );
  }
  function ready() {
    const value = state.workspace();
    if (value.status !== 'ready') throw new Error('Expected ready');
    return value;
  }
  function fail(url: string, code = 'INTERNAL_ERROR', status = 500, violations?: unknown) {
    http
      .expectOne(url)
      .flush({ code, detail: 'Secret', violations }, { status, statusText: 'Failure' });
  }
  function start(operation: 'create' | 'update' | 'delete') {
    return operation === 'create'
      ? management.create('b', content)
      : operation === 'update'
        ? management.update('b1', content)
        : management.delete('b1');
  }
  function url(operation: string) {
    return operation === 'create' ? '/api/columns/b/tasks' : '/api/tasks/b1';
  }
  it('appends the canonical create response only to its Column without extra GETs', async () => {
    const before = ready();
    const result = management.create('b', content);
    const request = http.expectOne('/api/columns/b/tasks');
    expect(request.request.body).toEqual(content);
    expect(state.workspace()).toBe(before);
    const canonical = {
      ...tasks('b')[0],
      id: 'new',
      title: 'Canonical',
      position: 3,
      updatedAt: 'new',
    };
    request.flush(canonical);
    expect(await result).toEqual({ success: true });
    expect(ready().columns[1].tasks).toEqual([...before.columns[1].tasks, canonical]);
    expect(ready().columns[0]).toBe(before.columns[0]);
    expect(ready().columns[2]).toBe(before.columns[2]);
  });
  it('replaces canonical content in place and derives current details from workspace state', async () => {
    management.selectedId.set('b1');
    const before = ready();
    const body = { ...content, description: null, dueDate: null };
    const result = management.update('b1', body);
    const request = http.expectOne('/api/tasks/b1');
    expect(request.request.body).toEqual(body);
    const canonical = { ...tasks('b')[1], ...body, title: 'Canonical', updatedAt: 'new' };
    request.flush(canonical);
    await result;
    expect(ready().columns[1].tasks).toEqual([
      before.columns[1].tasks[0],
      canonical,
      before.columns[1].tasks[2],
    ]);
    expect(management.selected()).toEqual(canonical);
    expect(ready().columns[0]).toBe(before.columns[0]);
    expect(ready().columns[2]).toBe(before.columns[2]);
  });
  it('removes only a confirmed deletion and compacts only its surviving Tasks', async () => {
    management.selectedId.set('b1');
    const before = ready();
    const result = management.delete('b1');
    expect(state.workspace()).toBe(before);
    http.expectOne('/api/tasks/b1').flush(null, { status: 204, statusText: 'No Content' });
    await result;
    expect(ready().columns[1].tasks.map((task) => [task.id, task.position])).toEqual([
      ['b0', 0],
      ['b2', 1],
    ]);
    expect(ready().columns[0]).toBe(before.columns[0]);
    expect(ready().columns[2]).toBe(before.columns[2]);
    expect(management.selected()).toBeNull();
  });
  it.each(['create', 'update', 'delete'] as const)(
    'keeps valid workspace on generic %s failure',
    async (operation) => {
      const before = state.workspace();
      const result = start(operation);
      fail(url(operation));
      expect(await result).toEqual({
        success: false,
        error: "We couldn't " + operation + ' the task. Please try again.',
      });
      expect(state.workspace()).toBe(before);
    },
  );
  it('reconciles COLUMN_NOT_FOUND on create', async () => {
    const result = start('create');
    fail(url('create'), 'COLUMN_NOT_FOUND', 404);
    await result;
    expect(state.workspace().status).toBe('loading');
    load();
  });
  it.each(['update', 'delete'] as const)(
    'announces TASK_NOT_FOUND during %s and reconciles',
    async (operation) => {
      management.selectedId.set('b1');
      const result = start(operation);
      fail(url(operation), 'TASK_NOT_FOUND', 404);
      await result;
      expect(management.notice()).toBe('This task is no longer available.');
      expect(management.selectedId()).toBeNull();
      expect(state.workspace().status).toBe('loading');
      load();
    },
  );
  it.each(['column', 'position', 'duplicate'])(
    'reconciles inconsistent create %s',
    async (kind) => {
      const result = start('create');
      http
        .expectOne(url('create'))
        .flush({
          ...tasks('b')[0],
          id: kind === 'duplicate' ? 'a0' : 'new',
          columnId: kind === 'column' ? 'a' : 'b',
          position: kind === 'position' ? 0 : 3,
        });
      await result;
      expect(state.workspace().status).toBe('loading');
      load();
    },
  );
  it.each(['column', 'position', 'id'])('reconciles inconsistent update %s', async (kind) => {
    const result = start('update');
    http
      .expectOne(url('update'))
      .flush({
        ...tasks('b')[1],
        id: kind === 'id' ? 'other' : 'b1',
        columnId: kind === 'column' ? 'a' : 'b',
        position: kind === 'position' ? 0 : 1,
      });
    await result;
    expect(state.workspace().status).toBe('loading');
    load();
  });
  it.each(['title', 'description', 'priority', 'dueDate', 'unknown', '__proto__'])(
    'maps validation field %s to safe copy without modifying workspace',
    async (field) => {
      const before = state.workspace();
      const result = start('update');
      fail(url('update'), 'VALIDATION_FAILED', 400, [{ field, message: 'Secret server message' }]);
      const failure = await result;
      expect(failure.success).toBe(false);
      if (!failure.success) {
        expect(failure.error).toBe('Check the task fields and try again.');
        expect(Object.keys(failure.fields ?? {})).toEqual(
          ['unknown', '__proto__'].includes(field) ? [] : [field],
        );
        expect(JSON.stringify(failure)).not.toContain('Secret');
      }
      expect(state.workspace()).toBe(before);
    },
  );
  it('handles MALFORMED_REQUEST safely', async () => {
    const result = start('create');
    fail(url('create'), 'MALFORMED_REQUEST', 400);
    expect(await result).toEqual({
      success: false,
      error: "We couldn't create the task. Please try again.",
    });
    expect(state.workspace().status).toBe('ready');
  });
  for (const transition of ['route', 'reload', 'destroy'] as const) {
    for (const outcome of ['success', 'error'] as const) {
      it.each(['create', 'update', 'delete'] as const)(
        `ignores late %s ${outcome} after ${transition}`,
        async (operation) => {
          const result = start(operation);
          const old = http.expectOne(url(operation));
          if (transition === 'route') {
            ids.next('two');
            load('two');
          } else if (transition === 'reload') {
            state.retry();
            load();
          } else TestBed.resetTestingModule();
          const before = state.workspace();
          if (outcome === 'error')
            old.flush(
              { code: 'TASK_NOT_FOUND', detail: 'Secret' },
              { status: 404, statusText: 'Not Found' },
            );
          else
            old.flush(
              operation === 'delete'
                ? null
                : {
                    ...tasks('b')[1],
                    id: operation === 'create' ? 'new' : 'b1',
                    position: operation === 'create' ? 3 : 1,
                  },
            );
          expect(await result).toMatchObject({ success: false, stale: true });
          expect(state.workspace()).toBe(before);
          expect(management.notice()).toBe('');
        },
      );
    }
  }
  it('serializes Task writes with Column writes in both directions', async () => {
    const task = start('create');
    const taskRequest = http.expectOne(url('create'));
    expect(columnManagement.busy()).toBe(true);
    await columnManagement.create('Column');
    await columnManagement.rename('b', 'B');
    await columnManagement.delete('b');
    await columnManagement.move('b', -1);
    await start('update');
    await start('delete');
    taskRequest.flush({ ...tasks('b')[0], id: 'new', position: 3 });
    await task;
    const column = columnManagement.move('b', -1);
    const columnRequest = http.expectOne('/api/boards/one/columns/order');
    expect(management.busy()).toBe(true);
    await start('create');
    await start('update');
    await start('delete');
    columnRequest.flush(columns);
    await column;
  });
  it('does not release a newer write when an old generation finishes', async () => {
    const old = start('create');
    const oldRequest = http.expectOne(url('create'));
    state.retry();
    load();
    const newer = start('update');
    const newRequest = http.expectOne(url('update'));
    oldRequest.flush({ ...tasks('b')[0], id: 'new', position: 3 });
    await old;
    expect(management.busy()).toBe(true);
    newRequest.flush(tasks('b')[1]);
    await newer;
    expect(management.busy()).toBe(false);
  });
});
