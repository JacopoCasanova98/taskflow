import { BoardStatisticsApi } from '../boards/statistics/board-statistics-api';
import { of as statisticsOf } from 'rxjs';
import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardWorkspace } from '../boards/board-workspace/board-workspace';
import { Task } from './task.models';
import { TaskDropListData } from './task-placement';
import { TaskView } from './task-view';

const tasks: Task[] = (['LOW', 'HIGH', 'MEDIUM'] as const).map((priority, position) => ({
  id: 'ABC'[position],
  columnId: position === 2 ? 'b' : 'a',
  title: ['Fix login', 'Payment', 'Callback'][position],
  description: position === 2 ? 'Implement oauth callback' : null,
  priority,
  position: position === 2 ? 0 : position,
  dueDate: null,
  createdAt: '',
  updatedAt: '',
}));
const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: '',
  updatedAt: '',
}));

describe('Task Search in the Board workspace', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let http: HttpTestingController;
  let element: HTMLElement;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let view: TaskView;
  beforeEach(async () => {
    vi.useFakeTimers();
    params = new BehaviorSubject(convertToParamMap({ boardId: 'one' }));
    TestBed.configureTestingModule({
      providers: [
        // This suite isolates its existing workspace behavior; statistics HTTP is covered separately.
        {
          provide: BoardStatisticsApi,
          useValue: {
            getStatistics: () =>
              statisticsOf({
                totalTasks: 0,
                overdueTasks: 0,
                priorityDistribution: { low: 0, medium: 0, high: 0 },
                statusDistribution: [],
              }),
          },
        },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    fixture = TestBed.createComponent(BoardWorkspace);
    http = TestBed.inject(HttpTestingController);
    element = fixture.nativeElement;
    view = fixture.debugElement.injector.get(TaskView);
    await load();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });
  async function settle() {
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    TestBed.tick();
  }
  async function load(id = 'one', response = tasks) {
    http.expectOne('/api/boards/' + id).flush({ id, name: id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(columns);
    http.expectOne('/api/boards/' + id + '/tasks').flush(response);
    await settle();
  }
  function canonical() {
    const state = fixture.componentInstance.state.workspace();
    if (state.status !== 'ready') throw new Error('Expected ready');
    return state;
  }
  function titles(lane = 0) {
    return Array.from(
      element.querySelectorAll('app-task-list')[lane].querySelectorAll('.task-title'),
      (n) => n.textContent?.trim(),
    );
  }
  function request(query: string, board = 'one') {
    return http.expectOne(
      (r) => r.url === '/api/boards/' + board + '/tasks/search' && r.params.get('q') === query,
    );
  }
  async function type(value: string) {
    const input = element.querySelector<HTMLInputElement>('#task-search')!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }
  async function search(query: string, result: Task[]) {
    await type(query);
    await vi.advanceTimersByTimeAsync(300);
    request(query.trim()).flush(result);
    await settle();
  }
  async function click(label: string) {
    const button = Array.from(element.querySelectorAll('button')).find(
      (b) => (b.getAttribute('aria-label') || b.textContent?.trim()) === label,
    )!;
    expect(button).toBeDefined();
    button.click();
    await settle();
  }
  it('labels a native search field and debounces only the latest distinct normalized query', async () => {
    expect(element.querySelector('label[for="task-search"]')?.textContent).toBe('Search tasks');
    expect(element.querySelector<HTMLInputElement>('#task-search')?.type).toBe('search');
    await type('l');
    await vi.advanceTimersByTimeAsync(100);
    await type('lo');
    await vi.advanceTimersByTimeAsync(100);
    await type('  log  ');
    await vi.advanceTimersByTimeAsync(299);
    http.expectNone((r) => r.url.endsWith('/search'));
    await vi.advanceTimersByTimeAsync(1);
    request('log').flush([tasks[0]]);
    await settle();
    await type('log');
    await vi.advanceTimersByTimeAsync(300);
    http.expectNone((r) => r.url.endsWith('/search'));
  });
  it('cancels a pending request as soon as a newer query arrives', async () => {
    await type('first');
    await vi.advanceTimersByTimeAsync(300);
    const first = request('first');
    await type('second');
    expect(first.cancelled).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    request('second').flush([tasks[1]]);
    await settle();
    expect(titles()).toEqual(['Payment']);
  });
  it('projects canonical objects in their own lanes and clears immediately without GETs', async () => {
    const before = canonical();
    await search('match', [{ ...tasks[1], title: 'Do not render search copy' }, tasks[2]]);
    expect(titles()).toEqual(['Payment']);
    expect(titles(1)).toEqual(['Callback']);
    expect(canonical()).toBe(before);
    await click('Clear search');
    expect(view.search.status()).toBe('idle');
    expect(view.manual()).toBe(true);
    expect(titles()).toEqual(['Fix login', 'Payment']);
    expect(canonical()).toBe(before);
    http.expectNone((r) => r.method === 'GET');
  });
  it('keeps canonical/prior rendering during loading and announces empty results only after success', async () => {
    await type('none');
    await vi.advanceTimersByTimeAsync(300);
    await settle();
    expect(element.textContent).toContain('Searching…');
    expect(titles()).toHaveLength(2);
    expect(element.textContent).not.toContain('No tasks match your search.');
    expect(element.querySelectorAll('app-task-list')[2].textContent).toContain('No tasks yet.');
    request('none').flush([]);
    await settle();
    expect(element.textContent).toContain('No tasks match your search.');
    await type('next');
    await vi.advanceTimersByTimeAsync(300);
    await settle();
    expect(element.textContent).not.toContain('No tasks match your search.');
    request('next').flush([tasks[0]]);
    await settle();
  });
  it('retains the workspace on safe search errors and retries immediately', async () => {
    const before = canonical();
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    request('login').flush({ detail: 'private SQL' }, { status: 500, statusText: 'Failure' });
    await settle();
    expect(canonical()).toBe(before);
    expect(element.textContent).toContain("We couldn't search tasks. Please try again.");
    expect(element.textContent).not.toContain('private SQL');
    await click('Retry search');
    request('login').flush([tasks[0]]);
    await settle();
    expect(titles()).toEqual(['Fix login']);
  });
  it('rejects overlong input safely without a request and recovers on a valid query', async () => {
    await type('x'.repeat(201));
    expect(view.search.status()).toBe('error');
    http.expectNone((r) => r.url.endsWith('/search'));
    await search('x'.repeat(200), []);
    expect(view.search.status()).toBe('ready');
  });
  it('turns BOARD_NOT_FOUND into the existing not-found workspace', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    request('login').flush(
      { code: 'BOARD_NOT_FOUND', detail: 'private' },
      { status: 404, statusText: 'Missing' },
    );
    await settle();
    expect(fixture.componentInstance.state.workspace().status).toBe('not-found');
    expect(element.textContent).not.toContain('private');
  });
  it('combines Search with Priority and Due while preserving existing sort', async () => {
    view.setDueDateFilter('OVERDUE');
    view.setOrder('PRIORITY_HIGH_TO_LOW');
    await search('match', tasks);
    expect(view.dueDateFilter()).toBe('OVERDUE');
    expect(titles()).toEqual([]);
    view.setFilter('HIGH');
    view.setDueDateFilter('NO_DUE_DATE');
    await settle();
    expect(view.search.input()).toBe('match');
    expect(view.filter()).toBe('HIGH');
    expect(titles()).toEqual(['Payment']);
    expect(canonical().columns[0].tasks.map((t) => t.position)).toEqual([0, 1]);
    http.expectNone((r) => r.url.endsWith('/search'));
  });
  it('disables CDK and rejects direct drops without any optimistic mutation or request', async () => {
    const lists = fixture.debugElement.queryAll(By.directive(CdkDropList));
    const container = lists[0].injector.get(CdkDropList) as CdkDropList<TaskDropListData>;
    const item = fixture.debugElement
      .query(By.directive(CdkDrag))
      .injector.get(CdkDrag) as CdkDrag<string>;
    const event = {
      container,
      previousContainer: container,
      item,
      previousIndex: 0,
      currentIndex: 1,
    } as CdkDragDrop<TaskDropListData, TaskDropListData, string>;
    const before = canonical();
    await search('login', [tasks[0]]);
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDrag)))
      expect(node.injector.get(CdkDrag).disabled).toBe(true);
    await fixture.componentInstance.tasks.drop(event);
    expect(canonical()).toBe(before);
    http.expectNone((r) => r.method === 'PUT');
    await click('Clear search');
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDrag)))
      expect(node.injector.get(CdkDrag).disabled).toBe(false);
  });
  it.each(['title', 'description'] as const)(
    'refreshes after a canonical %s edit without reloading or closing details',
    async (field) => {
      const original = field === 'title' ? tasks[0] : tasks[2];
      const query = field === 'title' ? 'login' : 'oauth';
      await search(query, [original]);
      fixture.componentInstance.tasks.selectedId.set(original.id);
      const updated = { ...original, [field]: 'Payment' };
      const mutation = fixture.componentInstance.tasks.update(original.id, {
        title: updated.title,
        description: updated.description,
        priority: updated.priority,
        dueDate: updated.dueDate,
      });
      http.expectOne('/api/tasks/' + original.id).flush(updated);
      await mutation;
      expect(fixture.componentInstance.tasks.selected()).toEqual(updated);
      request(query).flush([]);
      await settle();
      expect(titles(field === 'title' ? 0 : 1)).toEqual([]);
      expect(fixture.componentInstance.tasks.selected()).toEqual(updated);
      http.expectNone((r) => r.url === '/api/boards/one');
    },
  );
  it.each([true, false])(
    'refreshes after create, keeping matching=%s membership separate from canonical data',
    async (matches) => {
      await search('login', [tasks[0]]);
      const created = { ...tasks[0], id: 'new', title: matches ? 'Login' : 'Other', position: 2 };
      const mutation = fixture.componentInstance.tasks.create('a', {
        title: created.title,
        description: created.description,
        priority: created.priority,
        dueDate: created.dueDate,
      });
      http.expectOne('/api/columns/a/tasks').flush(created);
      await mutation;
      expect(canonical().columns[0].tasks[2]).toEqual(created);
      request('login').flush(matches ? [tasks[0], created] : [tasks[0]]);
      await settle();
      expect(titles()).toEqual(matches ? ['Fix login', 'Login'] : ['Fix login']);
      http.expectNone((r) => r.url === '/api/boards/one');
    },
  );
  it('removes confirmed deletes canonically and refreshes membership without workspace reload', async () => {
    await search('login', [tasks[0]]);
    const mutation = fixture.componentInstance.tasks.delete('A');
    http.expectOne('/api/tasks/A').flush(null);
    await mutation;
    expect(canonical().columns[0].tasks.map((t) => [t.id, t.position])).toEqual([['B', 0]]);
    request('login').flush([]);
    await settle();
    expect(titles()).toEqual([]);
  });
  it('resets query and cancels pending Search on Board navigation', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const old = request('login');
    params.next(convertToParamMap({ boardId: 'two' }));
    expect(old.cancelled).toBe(true);
    await load('two');
    expect(view.search.input()).toBe('');
    expect(view.search.status()).toBe('idle');
    expect(titles()).toHaveLength(2);
  });
  it('cancels pending Search on destruction', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const old = request('login');
    fixture.destroy();
    expect(old.cancelled).toBe(true);
  });
  it('cancels a pending debounce on destruction without issuing Search HTTP', async () => {
    await type('login');
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(300);
    http.expectNone((r) => r.url.endsWith('/search'));
  });
  it('preserves Search on same-Board reload and runs it exactly once after ready', async () => {
    await search('login', [tasks[0]]);
    fixture.componentInstance.state.retry();
    await settle();
    expect(view.search.input()).toBe('login');
    http.expectNone((r) => r.url.endsWith('/search'));
    await load();
    request('login').flush([tasks[0]]);
    await settle();
    http.expectNone((r) => r.url.endsWith('/search'));
  });
  it('ignores late responses during reload before effects run', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const old = request('login');
    fixture.componentInstance.state.retry();
    old.flush({ code: 'BOARD_NOT_FOUND' }, { status: 404, statusText: 'Missing' });
    await load();
    request('login').flush([tasks[0]]);
    await settle();
    expect(canonical().board.id).toBe('one');
  });
  it('reconciles unknown IDs once, preserves query, and prevents automatic reload loops', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    request('login').flush([{ ...tasks[0], id: 'unknown' }]);
    await settle();
    await load();
    request('login').flush([{ ...tasks[0], id: 'unknown' }]);
    await settle();
    expect(view.search.status()).toBe('error');
    expect(canonical().columns[0].tasks).toEqual(tasks.slice(0, 2));
    http.expectNone((r) => r.url === '/api/boards/one');
  });
  it('retains the prior successful projection while the next query loads', async () => {
    await search('login', [tasks[0]]);
    await type('payment');
    await vi.advanceTimersByTimeAsync(300);
    await settle();
    expect(titles()).toEqual(['Fix login']);
    expect(element.textContent).toContain('Searching…');
    request('payment').flush([tasks[1]]);
    await settle();
    expect(titles()).toEqual(['Payment']);
  });
  it('announces no matches even for a Board without Columns', async () => {
    fixture.componentInstance.state.retry();
    http
      .expectOne('/api/boards/one')
      .flush({ id: 'one', name: 'Empty', createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/one/columns').flush([]);
    await settle();
    await search('login', []);
    expect(element.textContent).toContain('No tasks match your search.');
  });
  it('cancels pending Search on same-Board reload and reruns after loading', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const old = request('login');
    fixture.componentInstance.state.retry();
    await settle();
    expect(old.cancelled).toBe(true);
    await load();
    request('login').flush([tasks[0]]);
    await settle();
    expect(titles()).toEqual(['Fix login']);
  });
  it('ignores late successes during reload without triggering another reconciliation', async () => {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const old = request('login');
    fixture.componentInstance.state.retry();
    old.flush([{ ...tasks[0], id: 'old-only' }]);
    await load();
    request('login').flush([tasks[0]]);
    await settle();
    expect(titles()).toEqual(['Fix login']);
  });
  it('keeps search membership and canonical data intact after a failed Task edit', async () => {
    await search('login', [tasks[0]]);
    const before = canonical();
    const mutation = fixture.componentInstance.tasks.update('A', {
      title: 'Other',
      description: null,
      priority: 'LOW',
      dueDate: null,
    });
    http.expectOne('/api/tasks/A').flush({}, { status: 500, statusText: 'Failure' });
    await mutation;
    await settle();
    http.expectNone((r) => r.url.endsWith('/search'));
    expect(canonical()).toBe(before);
    expect(titles()).toEqual(['Fix login']);
  });
  it('sorts Search membership HIGH/MEDIUM/LOW without changing manual positions', async () => {
    fixture.componentInstance.state.retry();
    await load(
      'one',
      tasks.map((t, position) => ({ ...t, columnId: 'a', position })),
    );
    const before = canonical();
    view.setOrder('PRIORITY_HIGH_TO_LOW');
    await search('all', tasks);
    expect(titles()).toEqual(['Payment', 'Callback', 'Fix login']);
    expect(canonical()).toBe(before);
    expect(canonical().columns[0].tasks.map((t) => t.position)).toEqual([0, 1, 2]);
    http.expectNone((r) => r.method === 'PUT');
  });
  it('clears pending debounce immediately for whitespace and never requests a blank query', async () => {
    await type('login');
    await type('   ');
    expect(view.search.active()).toBe(false);
    await vi.advanceTimersByTimeAsync(300);
    http.expectNone((r) => r.url.endsWith('/search'));
    expect(view.manual()).toBe(true);
  });
  it('uses refreshed canonical data after reconciling an unknown result', async () => {
    const unknown = { ...tasks[0], id: 'new', title: 'Canonical new', position: 2 };
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    request('login').flush([unknown]);
    await settle();
    await load('one', [...tasks, unknown]);
    request('login').flush([{ ...unknown, title: 'Search copy' }]);
    await settle();
    expect(titles()).toEqual(['Canonical new']);
  });
});
