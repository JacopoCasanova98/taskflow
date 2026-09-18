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
import { Column } from '../columns/column.models';
import { Task, UpdateTaskRequest } from './task.models';
import { TaskDropListData } from './task-placement';
import { TaskView } from './task-view';

const columns: Column[] = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: ['Review', 'Custom workflow', 'Inbox'][position],
  position,
  createdAt: '',
  updatedAt: '',
}));
const tasks: Task[] = [
  { id: 'T1', columnId: 'a', priority: 'HIGH', dueDate: '2026-09-11', position: 0 },
  { id: 'T2', columnId: 'a', priority: 'LOW', dueDate: '2026-09-11', position: 1 },
  { id: 'T3', columnId: 'b', priority: 'HIGH', dueDate: '2026-09-11', position: 0 },
  { id: 'T4', columnId: 'b', priority: 'HIGH', dueDate: '2026-09-13', position: 1 },
  { id: 'T5', columnId: 'a', priority: 'HIGH', dueDate: '2026-09-12', position: 2 },
].map((t) => ({ ...t, title: t.id, description: 'login', createdAt: '', updatedAt: '' }) as Task);
const content = (task: Task): UpdateTaskRequest => ({
  title: task.title,
  description: task.description,
  priority: task.priority,
  dueDate: task.dueDate,
});

describe('Combined Task filters in the Board workspace', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let http: HttpTestingController;
  let element: HTMLElement;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let view: TaskView;
  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 12, 23, 59, 59));
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
  async function load(id = 'one', response = tasks, lanes = columns) {
    http.expectOne('/api/boards/' + id).flush({ id, name: id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(lanes);
    if (lanes.length)
      http
        .expectOne('/api/boards/' + id + '/tasks')
        .flush(response.filter((task) => lanes.some((lane) => lane.id === task.columnId)));
    await settle();
  }
  function canonical() {
    const state = fixture.componentInstance.state.workspace();
    if (state.status !== 'ready') throw new Error('Expected ready');
    return state;
  }
  function lane(index = 0) {
    return element.querySelectorAll('app-task-list')[index];
  }
  function titles(index = 0) {
    return Array.from(lane(index).querySelectorAll('.task-title'), (b) => b.textContent?.trim());
  }
  function control(id: string) {
    return element.querySelector<HTMLSelectElement>('#' + id)!;
  }
  async function change(id: string, value: string) {
    const select = control(id);
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
  }
  function button(label: string) {
    const found = Array.from(element.querySelectorAll('button')).find(
      (b) => (b.getAttribute('aria-label') || b.textContent?.trim()) === label,
    );
    expect(found).toBeDefined();
    return found!;
  }
  async function click(label: string) {
    button(label).click();
    await settle();
  }
  function options() {
    return Array.from(control('task-column-filter').options, (o) => [
      o.value,
      o.textContent?.trim(),
    ]);
  }
  async function type(value: string) {
    const input = element.querySelector<HTMLInputElement>('#task-search')!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }
  function searchRequest(query = 'login') {
    return http.expectOne(
      (r) => r.url === '/api/boards/one/tasks/search' && r.params.get('q') === query,
    );
  }
  async function search(result = tasks.slice(0, 4)) {
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    searchRequest().flush(result);
    await settle();
  }
  async function localFilters() {
    await change('task-column-filter', 'a');
    await change('task-priority-filter', 'HIGH');
    await change('task-due-filter', 'OVERDUE');
  }
  function assertFilters(query = 'login', column = 'a') {
    expect(view.search.input()).toBe(query);
    expect(view.columnFilter()).toBe(column);
    expect(view.filter()).toBe('HIGH');
    expect(view.dueDateFilter()).toBe('OVERDUE');
  }
  function dragDisabled(value: boolean) {
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDrag)))
      expect(node.injector.get(CdkDrag).disabled).toBe(value);
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDropList)))
      expect(node.injector.get(CdkDropList).disabled).toBe(value);
  }
  function dropEvent() {
    const container = fixture.debugElement
      .queryAll(By.directive(CdkDropList))[0]
      .injector.get(CdkDropList) as CdkDropList<TaskDropListData>;
    const item = fixture.debugElement
      .query(By.directive(CdkDrag))
      .injector.get(CdkDrag) as CdkDrag<string>;
    return {
      container,
      previousContainer: container,
      item,
      previousIndex: 0,
      currentIndex: 1,
    } as CdkDragDrop<TaskDropListData, TaskDropListData, string>;
  }

  it('sorts combined Search membership locally and preserves sorting when clearing filters', async () => {
    const response = [
      ...tasks,
      { ...tasks[0], id: 'T6', title: 'T6', position: 3, updatedAt: '2026-09-12T13:00:00Z' },
    ];
    fixture.componentInstance.state.retry();
    await load('one', response);
    await localFilters();
    await search(response.slice(0, 4).concat(response[5]));
    const before = canonical();
    for (const order of [
      'CREATED_NEWEST',
      'DUE_DATE_ASC',
      'PRIORITY_LOW_TO_HIGH',
      'UPDATED_OLDEST',
      'MANUAL',
    ]) {
      await change('task-priority-order', order);
      expect(titles()).toEqual(['T1', 'T6']);
      http.expectNone(() => true);
    }
    await change('task-priority-order', 'UPDATED_NEWEST');
    expect(titles()).toEqual(['T6', 'T1']);
    await click('Clear filters');
    expect(view.order()).toBe('UPDATED_NEWEST');
    expect(titles()).toEqual(['T6', 'T1', 'T2', 'T5']);
    expect(titles(1)).toEqual(['T3', 'T4']);
    expect(canonical()).toBe(before);
    http.expectNone(() => true);
  });
  it('labels native controls and derives Column UUID/name options in canonical order', () => {
    for (const [id, label] of [
      ['task-search', 'Search tasks'],
      ['task-column-filter', 'Column'],
      ['task-priority-filter', 'Priority'],
      ['task-due-filter', 'Due date'],
      ['task-priority-order', 'Task order'],
    ])
      expect(element.querySelector('label[for="' + id + '"]')?.textContent).toBe(label);
    expect(options()).toEqual([
      ['ALL', 'All columns'],
      ['a', 'Review'],
      ['b', 'Custom workflow'],
      ['c', 'Inbox'],
    ]);
    expect(button('Clear filters').disabled).toBe(true);
    expect(view.activeFilterCount()).toBe(0);
  });
  it('shows only All columns for a zero-Column Board', async () => {
    fixture.componentInstance.state.retry();
    await load('one', [], []);
    expect(options()).toEqual([['ALL', 'All columns']]);
    expect(view.columnFilter()).toBe('ALL');
  });
  it('combines all four filters with AND while retaining every Column lane', async () => {
    const before = canonical();
    await localFilters();
    await search();
    assertFilters();
    expect(titles()).toEqual(['T1']);
    expect(titles(1)).toEqual([]);
    expect(titles(2)).toEqual([]);
    expect(element.querySelectorAll('app-task-list')).toHaveLength(3);
    expect(canonical()).toBe(before);
    expect(element.textContent).toContain('4 filters active');
    expect(lane(1).textContent).toContain('No tasks match the current filters.');
    expect(lane(2).textContent).toContain('No tasks yet.');
  });
  it('changes local filters without making Search requests or resetting the other dimensions', async () => {
    await search();
    await localFilters();
    assertFilters();
    expect(titles()).toEqual(['T1']);
    await change('task-priority-filter', 'LOW');
    expect(titles()).toEqual(['T2']);
    await change('task-column-filter', 'b');
    expect(titles(1)).toEqual([]);
    expect(view.search.input()).toBe('login');
    expect(view.dueDateFilter()).toBe('OVERDUE');
    http.expectNone((r) => r.method === 'GET');
  });
  it('keeps local filters on prior Search membership while a new query loads or fails', async () => {
    await localFilters();
    await search();
    await type('next');
    await vi.advanceTimersByTimeAsync(300);
    await settle();
    expect(titles()).toEqual(['T1']);
    expect(element.textContent).toContain('Searching…');
    await change('task-column-filter', 'b');
    expect(titles(1)).toEqual(['T3']);
    searchRequest('next').flush({ detail: 'Private' }, { status: 500, statusText: 'Failure' });
    await settle();
    assertFilters('next', 'b');
    expect(titles(1)).toEqual(['T3']);
    expect(element.textContent).not.toContain('Private');
    await click('Retry search');
    searchRequest('next').flush([tasks[3]]);
    await settle();
    assertFilters('next', 'b');
    expect(titles(1)).toEqual([]);
    expect(canonical().status).toBe('ready');
  });
  it('Clear filters cancels pending Search immediately, preserves order, and restores full membership', async () => {
    await localFilters();
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const pending = searchRequest();
    const before = canonical();
    await click('Clear filters');
    expect(pending.cancelled).toBe(true);
    expect(view.search.active()).toBe(false);
    expect(view.columnFilter()).toBe('ALL');
    expect(view.filter()).toBe('ALL');
    expect(view.dueDateFilter()).toBe('ALL');
    expect(view.order()).toBe('PRIORITY_HIGH_TO_LOW');
    expect(titles()).toEqual(['T1', 'T5', 'T2']);
    expect(view.activeFilterCount()).toBe(0);
    expect(button('Clear filters').disabled).toBe(true);
    dragDisabled(true);
    await change('task-priority-order', 'MANUAL');
    expect(titles()).toEqual(['T1', 'T2', 'T5']);
    expect(canonical()).toBe(before);
    dragDisabled(false);
    http.expectNone((r) => r.method === 'PUT' || r.method === 'GET');
  });
  it('counts dimensions, not order, and Clear search alone retains local filters', async () => {
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    expect(view.activeFilterCount()).toBe(0);
    await change('task-column-filter', 'a');
    expect(view.activeFilterCount()).toBe(1);
    expect(element.textContent).toContain('1 filter active');
    await search();
    expect(view.activeFilterCount()).toBe(2);
    await click('Clear search');
    expect(view.columnFilter()).toBe('a');
    expect(view.activeFilterCount()).toBe(1);
    expect(view.order()).toBe('PRIORITY_LOW_TO_HIGH');
  });
  it('distinguishes true empty lanes, Search-only misses, and combined misses', async () => {
    await search([]);
    expect(lane(0).textContent).toContain('No tasks match your search.');
    expect(lane(2).textContent).toContain('No tasks yet.');
    await change('task-priority-filter', 'HIGH');
    expect(lane(0).textContent).toContain('No tasks match the current filters.');
    expect(element.textContent).not.toContain('No tasks match your search.');
    expect(lane(2).textContent).toContain('No tasks yet.');
  });
  it.each(['column', 'priority', 'due', 'search'])(
    'disables CDK and defensively rejects direct drops for %s',
    async (dimension) => {
      const event = dropEvent();
      const before = canonical();
      if (dimension === 'column') await change('task-column-filter', 'a');
      if (dimension === 'priority') await change('task-priority-filter', 'HIGH');
      if (dimension === 'due') await change('task-due-filter', 'OVERDUE');
      if (dimension === 'search') await search();
      dragDisabled(true);
      await fixture.componentInstance.tasks.drop(event);
      expect(canonical()).toBe(before);
      http.expectNone((r) => r.url.endsWith('/placement'));
      await click('Clear filters');
      dragDisabled(false);
    },
  );
  it('updates filter options for Column creation and renaming without extra HTTP', async () => {
    await localFilters();
    const added = { ...columns[2], id: 'new', name: 'Custom new', position: 3 };
    const creation = fixture.componentInstance.columns.create(added.name);
    http.expectOne('/api/boards/one/columns').flush(added);
    await creation;
    await settle();
    expect(options()[4]).toEqual(['new', 'Custom new']);
    expect(view.columnFilter()).toBe('a');
    const rename = fixture.componentInstance.columns.rename('a', 'Renamed');
    http.expectOne('/api/columns/a').flush({ ...columns[0], name: 'Renamed' });
    await rename;
    await settle();
    expect(options()[1]).toEqual(['a', 'Renamed']);
    expect(view.columnFilter()).toBe('a');
    http.expectNone((r) => r.method === 'GET');
  });
  it('follows canonical Column reordering in options and lanes while retaining selection', async () => {
    await localFilters();
    const reorder = fixture.componentInstance.columns.move('b', -1);
    http
      .expectOne('/api/boards/one/columns/order')
      .flush([columns[1], columns[0], columns[2]].map((c, position) => ({ ...c, position })));
    await reorder;
    await settle();
    expect(options().map((o) => o[0])).toEqual(['ALL', 'b', 'a', 'c']);
    expect(view.columnFilter()).toBe('a');
    expect(titles(1)).toEqual(['T1']);
    http.expectNone((r) => r.method === 'GET');
  });
  it.each([true, false])(
    'normalizes Column selection only when the deleted Column was selected=%s',
    async (selected) => {
      await change('task-column-filter', selected ? 'c' : 'a');
      await change('task-priority-filter', 'HIGH');
      const deletion = fixture.componentInstance.columns.delete('c');
      http.expectOne('/api/columns/c').flush(null);
      await deletion;
      await settle();
      expect(view.columnFilter()).toBe(selected ? 'ALL' : 'a');
      expect(view.filter()).toBe('HIGH');
      expect(options()).toHaveLength(3);
      http.expectNone((r) => r.method === 'GET');
    },
  );
  it('normalizes invalid Column IDs and accepts only canonical options', async () => {
    view.setColumnFilter('foreign');
    await settle();
    expect(view.columnFilter()).toBe('ALL');
    view.setColumnFilter('a');
    await settle();
    expect(view.columnFilter()).toBe('a');
  });
  it.each([true, false])(
    'creates canonical Tasks under combined Search/filters with matching=%s',
    async (matches) => {
      await localFilters();
      await search();
      const created = {
        ...tasks[0],
        id: 'new',
        title: 'New',
        position: 3,
        priority: matches ? ('HIGH' as const) : ('LOW' as const),
      };
      const mutation = fixture.componentInstance.tasks.create('a', content(created));
      http.expectOne('/api/columns/a/tasks').flush(created);
      await mutation;
      expect(canonical().columns[0].tasks[3]).toEqual(created);
      searchRequest().flush([tasks[0], created]);
      await settle();
      expect(titles()).toEqual(matches ? ['T1', 'New'] : ['T1']);
      assertFilters();
      http.expectNone('/api/boards/one');
    },
  );
  it('edits can leave combined membership while selected details stay canonical', async () => {
    await localFilters();
    await search();
    await click('View task: T1');
    const updated = { ...tasks[0], priority: 'LOW' as const };
    const mutation = fixture.componentInstance.tasks.update('T1', content(updated));
    http.expectOne('/api/tasks/T1').flush(updated);
    await mutation;
    expect(fixture.componentInstance.tasks.selected()).toEqual(updated);
    searchRequest().flush([updated]);
    await settle();
    expect(titles()).toEqual([]);
    expect(fixture.componentInstance.tasks.selected()).toEqual(updated);
    assertFilters();
    http.expectNone('/api/boards/one');
  });
  it('editing due date can enter local combined filters without Search or a workspace reload', async () => {
    await localFilters();
    const updated = { ...tasks[4], dueDate: '2026-09-11' };
    const mutation = fixture.componentInstance.tasks.update('T5', content(updated));
    http.expectOne('/api/tasks/T5').flush(updated);
    await mutation;
    await settle();
    expect(titles()).toEqual(['T1', 'T5']);
    assertFilters('');
    http.expectNone((r) => r.method === 'GET');
  });
  it('confirmed deletion removes canonical membership and retains all filters', async () => {
    await localFilters();
    await search();
    const mutation = fixture.componentInstance.tasks.delete('T1');
    http.expectOne('/api/tasks/T1').flush(null);
    await mutation;
    searchRequest().flush([tasks[1]]);
    await settle();
    expect(titles()).toEqual([]);
    expect(canonical().columns[0].tasks.map((t) => [t.id, t.position])).toEqual([
      ['T2', 0],
      ['T5', 1],
    ]);
    assertFilters();
  });
  it('keeps details and an unsent draft when combined filters hide the card', async () => {
    await click('View task: T1');
    await click('Edit');
    const form = element.querySelector('form');
    const title = element.querySelector<HTMLInputElement>('form input')!;
    title.value = 'Draft';
    title.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    await change('task-column-filter', 'b');
    await change('task-priority-filter', 'LOW');
    expect(titles()).toEqual([]);
    expect(fixture.componentInstance.tasks.selected()?.id).toBe('T1');
    expect(element.querySelector('form')).toBe(form);
    expect(title.value).toBe('Draft');
  });
  it.each([true, false])(
    'preserves same-Board filters and normalizes the selected Column only if it disappeared=%s',
    async (removed) => {
      await localFilters();
      await search();
      await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
      fixture.componentInstance.state.retry();
      await settle();
      assertFilters();
      await load('one', tasks, removed ? columns.slice(1) : columns);
      searchRequest().flush(removed ? [tasks[2]] : [tasks[0]]);
      await settle();
      assertFilters('login', removed ? 'ALL' : 'a');
      expect(view.order()).toBe('PRIORITY_LOW_TO_HIGH');
      http.expectNone((r) => r.url.endsWith('/search'));
    },
  );
  it('preserves every local filter through bounded unknown-Search-ID reconciliation', async () => {
    await localFilters();
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const added = { ...tasks[0], id: 'new', position: 3 };
    searchRequest().flush([added]);
    await settle();
    assertFilters();
    await load('one', [...tasks, added]);
    searchRequest().flush([added]);
    await settle();
    assertFilters();
    expect(titles()).toEqual(['T1']);
    http.expectNone('/api/boards/one');
  });
  it('resets every dimension and order on Board navigation and cancels pending Search', async () => {
    await localFilters();
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    await type('login');
    await vi.advanceTimersByTimeAsync(300);
    const pending = searchRequest();
    params.next(convertToParamMap({ boardId: 'two' }));
    expect(pending.cancelled).toBe(true);
    await load('two');
    expect(view.search.input()).toBe('');
    expect(view.columnFilter()).toBe('ALL');
    expect(view.filter()).toBe('ALL');
    expect(view.dueDateFilter()).toBe('ALL');
    expect(view.order()).toBe('MANUAL');
    dragDisabled(false);
  });
  it('recomputes combined due filtering at local midnight without HTTP or other filter changes', async () => {
    await change('task-column-filter', 'a');
    await change('task-priority-filter', 'HIGH');
    await change('task-due-filter', 'DUE_TODAY');
    expect(titles()).toEqual(['T5']);
    const before = canonical();
    await vi.advanceTimersByTimeAsync(1000);
    await settle();
    expect(titles()).toEqual([]);
    expect(view.columnFilter()).toBe('a');
    expect(view.filter()).toBe('HIGH');
    expect(view.dueDateFilter()).toBe('DUE_TODAY');
    expect(canonical()).toBe(before);
    http.expectNone((r) => r.method === 'GET');
  });
});
