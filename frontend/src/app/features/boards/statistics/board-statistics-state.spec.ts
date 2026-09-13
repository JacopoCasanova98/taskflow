import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { TaskLocalDay } from '../../tasks/task-local-day';
import { BoardWorkspaceState } from '../board-workspace/board-workspace-state';
import { BoardStatisticsState } from './board-statistics-state';

const zero = {
  totalTasks: 0,
  overdueTasks: 0,
  priorityDistribution: { low: 0, medium: 0, high: 0 },
  statusDistribution: [],
};
describe('Board statistics state with real workspace and HTTP', () => {
  let http: HttpTestingController;
  let workspace: BoardWorkspaceState;
  let statistics: BoardStatisticsState;
  let ids: BehaviorSubject<string>;
  const today = signal('2026-09-13');
  beforeEach(() => {
    today.set('2026-09-13');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BoardWorkspaceState,
        BoardStatisticsState,
        { provide: TaskLocalDay, useValue: { today } },
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    workspace = TestBed.inject(BoardWorkspaceState);
    statistics = TestBed.inject(BoardStatisticsState);
    ids = new BehaviorSubject('a');
    workspace.connect(ids);
    TestBed.tick();
  });
  afterEach(() => http.verify());
  function ready(id = 'a') {
    http.expectOne('/api/boards/' + id).flush({ id, name: id });
    http.expectOne('/api/boards/' + id + '/columns').flush([]);
    TestBed.tick();
  }
  function request(id = 'a', day = today()) {
    return http.expectOne('/api/boards/' + id + '/statistics?asOf=' + day);
  }
  it('waits for workspace identity, announces loading, and exposes server data', () => {
    expect(statistics.state().status).toBe('loading');
    http.expectNone((r) => r.url.endsWith('/statistics'));
    ready();
    expect(statistics.state().status).toBe('loading');
    request().flush(zero);
    expect(statistics.state()).toEqual({ status: 'ready', data: zero });
  });
  it('isolates generic failure and retries statistics only', () => {
    ready();
    request().flush({ detail: 'secret' }, { status: 500, statusText: 'Failure' });
    expect(statistics.state()).toEqual({ status: 'error' });
    expect(workspace.workspace().status).toBe('ready');
    statistics.retry();
    TestBed.tick();
    expect(statistics.state().status).toBe('loading');
    request().flush(zero);
    expect(statistics.state().status).toBe('ready');
  });
  it('reconciles BOARD_NOT_FOUND through the existing workspace state', () => {
    ready();
    request().flush({ code: 'BOARD_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(workspace.workspace().status).toBe('not-found');
  });
  it('cancels old Board requests and cannot display A data on B', () => {
    ready();
    const old = request();
    ids.next('b');
    TestBed.tick();
    expect(old.cancelled).toBe(true);
    ready('b');
    request('b').flush({ ...zero, totalTasks: 9 });
    expect(statistics.state()).toEqual({ status: 'ready', data: { ...zero, totalTasks: 9 } });
  });
  it('ignores an old response even before the route cancellation effect runs', () => {
    ready();
    const old = request();
    ids.next('b');
    old.flush({ ...zero, totalTasks: 99 });
    expect(statistics.state().status).toBe('loading');
    ready('b');
    request('b').flush(zero);
    expect(statistics.state()).toEqual({ status: 'ready', data: zero });
  });
  it('refreshes on same-Board reconciliation only after canonical readiness', () => {
    ready();
    request().flush(zero);
    workspace.retry();
    TestBed.tick();
    http.expectNone((r) => r.url.endsWith('/statistics'));
    ready();
    request().flush({ ...zero, totalTasks: 2 });
    expect(statistics.state()).toEqual({ status: 'ready', data: { ...zero, totalTasks: 2 } });
  });
  it('refreshes just statistics at local midnight and cancels the previous date', () => {
    ready();
    const old = request();
    today.set('2026-09-14');
    TestBed.tick();
    expect(old.cancelled).toBe(true);
    request('a', '2026-09-14').flush({ ...zero, overdueTasks: 1 });
    http.expectNone((r) => !r.url.endsWith('/statistics'));
    expect(statistics.state()).toEqual({ status: 'ready', data: { ...zero, overdueTasks: 1 } });
  });
  it('ignores old-day data before the midnight effect runs', () => {
    ready();
    const old = request();
    today.set('2026-09-14');
    old.flush({ ...zero, totalTasks: 99 });
    expect(statistics.state().status).toBe('loading');
    TestBed.tick();
    request().flush(zero);
  });
  it('coalesces synchronous invalidations and ignores optimistic array changes and stale generations', () => {
    ready();
    request().flush(zero);
    workspace.updateColumns(workspace.generation(), (columns) => [...columns]);
    TestBed.tick();
    workspace.mutationConfirmed(workspace.generation() - 1);
    TestBed.tick();
    http.expectNone((r) => r.url.endsWith('/statistics'));
    workspace.mutationConfirmed(workspace.generation());
    workspace.mutationConfirmed(workspace.generation());
    TestBed.tick();
    request().flush(zero);
  });
});
