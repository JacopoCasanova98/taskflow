import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { TaskLocalDay } from '../../tasks/task-local-day';
import { TaskDropListData } from '../../tasks/task-placement';
import { Task } from '../../tasks/task.models';
import { BoardWorkspace } from '../board-workspace/board-workspace';

const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  position,
  name: ['Backlog', 'Doing', 'Done'][position],
  createdAt: '',
  updatedAt: '',
}));
const task: Task = {
  id: 't',
  columnId: 'a',
  title: 'First',
  description: null,
  priority: 'HIGH',
  dueDate: '2026-09-12',
  position: 0,
  createdAt: '',
  updatedAt: '',
};
const data = {
  totalTasks: 4,
  overdueTasks: 1,
  priorityDistribution: { high: 2, medium: 1, low: 1 },
  statusDistribution: columns.map((c) => ({
    columnId: c.id,
    name: c.name,
    position: c.position,
    taskCount: c.position === 0 ? 4 : 0,
  })),
};
const zero = {
  totalTasks: 0,
  overdueTasks: 0,
  priorityDistribution: { high: 0, medium: 0, low: 0 },
  statusDistribution: data.statusDistribution.map((c) => ({ ...c, taskCount: 0 })),
};

describe('Board statistics dashboard and mutation integration', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let http: HttpTestingController;
  let element: HTMLElement;
  const today = signal('2026-09-13');
  beforeEach(async () => {
    vi.useFakeTimers();
    today.set('2026-09-13');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ boardId: 'one' })) },
        },
      ],
    });
    TestBed.overrideComponent(BoardWorkspace, {
      add: { providers: [{ provide: TaskLocalDay, useValue: { today } }] },
    });
    fixture = TestBed.createComponent(BoardWorkspace);
    http = TestBed.inject(HttpTestingController);
    element = fixture.nativeElement;
    http
      .expectOne('/api/boards/one')
      .flush({ id: 'one', name: 'Product', createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/one/columns').flush(columns);
    http.expectOne('/api/boards/one/tasks').flush([task]);
    await settle();
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
  function stats() {
    return http.expectOne('/api/boards/one/statistics?asOf=' + today());
  }
  function panel() {
    return element.querySelector('app-board-statistics')!;
  }
  async function loaded() {
    stats().flush(data);
    await settle();
  }
  function counts() {
    return Array.from(panel().querySelectorAll('dl > div'), (row) => [
      row.querySelector('dt')?.textContent,
      row.querySelector('dd')?.textContent,
    ]);
  }

  it('announces loading and renders text counts and every Column in server order including zero', async () => {
    expect(panel().querySelector('[role="status"]')?.textContent).toContain(
      'Loading board statistics',
    );
    await loaded();
    expect(panel().querySelector('h2')?.textContent).toBe('Board statistics');
    expect(counts()).toEqual([
      ['Total tasks', '4'],
      ['Overdue', '1'],
      ['High', '2'],
      ['Medium', '1'],
      ['Low', '1'],
      ['Backlog', '4'],
      ['Doing', '0'],
      ['Done', '0'],
    ]);
    expect(panel().textContent).toContain('regardless of search, filters, or order');
    expect(panel().textContent).not.toMatch(/Completed|Open/);
    expect(panel().querySelector('section')?.getAttribute('aria-labelledby')).toBe(
      panel().querySelector('h2')?.id,
    );
  });
  it('keeps the dashboard visible for zero tasks', async () => {
    stats().flush(zero);
    await settle();
    expect(counts().map((row) => row[1])).toEqual(Array(8).fill('0'));
  });
  it('shows safe statistics error and a working Retry while retaining the Board and lanes', async () => {
    stats().flush({ detail: 'secret SQL' }, { status: 500, statusText: 'Failure' });
    await settle();
    expect(panel().querySelector('[role="alert"]')?.textContent).toBe(
      "We couldn't load board statistics.",
    );
    expect(element.querySelector('h1')?.textContent).toBe('Product');
    expect(element.querySelectorAll('app-task-list').length).toBe(3);
    expect(element.textContent).not.toContain('secret SQL');
    const retry = panel().querySelector('button')!;
    expect(retry.type).toBe('button');
    expect(retry.textContent).toBe('Retry');
    retry.click();
    await settle();
    stats().flush(data);
    await settle();
    expect(counts()[0]).toEqual(['Total tasks', '4']);
  });
  it.each([
    'Task create',
    'Task edit',
    'Task delete',
    'Column create',
    'Column rename',
    'Column delete',
    'Column reorder',
  ])('refreshes exactly once after confirmed %s, never at request start', async (operation) => {
    await loaded();
    const page = fixture.componentInstance;
    let pending: Promise<unknown>;
    let url: string;
    let response: object | null;
    switch (operation) {
      case 'Task create':
        pending = page.tasks.create('b', {
          title: 'New',
          description: null,
          priority: 'LOW',
          dueDate: null,
        });
        url = '/api/columns/b/tasks';
        response = { ...task, id: 'new', columnId: 'b', priority: 'LOW', dueDate: null };
        break;
      case 'Task edit':
        pending = page.tasks.update('t', {
          title: 'Edited',
          description: null,
          priority: 'MEDIUM',
          dueDate: null,
        });
        url = '/api/tasks/t';
        response = { ...task, title: 'Edited', priority: 'MEDIUM', dueDate: null };
        break;
      case 'Task delete':
        pending = page.tasks.delete('t');
        url = '/api/tasks/t';
        response = null;
        break;
      case 'Column create':
        pending = page.columns.create('Review');
        url = '/api/boards/one/columns';
        response = { ...columns[0], id: 'd', name: 'Review', position: 3 };
        break;
      case 'Column rename':
        pending = page.columns.rename('b', 'Review');
        url = '/api/columns/b';
        response = { ...columns[1], name: 'Review' };
        break;
      case 'Column delete':
        pending = page.columns.delete('c');
        url = '/api/columns/c';
        response = null;
        break;
      default:
        pending = page.columns.move('a', 1);
        url = '/api/boards/one/columns/order';
        response = [columns[1], columns[0], columns[2]].map((c, position) => ({ ...c, position }));
    }
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
    http.expectOne(url).flush(response);
    await pending;
    await settle();
    stats().flush(data);
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
  });
  function drop() {
    const lists = fixture.debugElement.queryAll(By.directive(CdkDropList));
    return {
      previousContainer: lists[0].injector.get(CdkDropList),
      container: lists[1].injector.get(CdkDropList),
      item: fixture.debugElement.query(By.directive(CdkDrag)).injector.get(CdkDrag),
      previousIndex: 0,
      currentIndex: 0,
    } as CdkDragDrop<TaskDropListData, TaskDropListData, string>;
  }
  it('refreshes after confirmed drag placement, not the optimistic move', async () => {
    await loaded();
    const pending = fixture.componentInstance.tasks.drop(drop());
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
    http.expectOne('/api/tasks/t/placement').flush({ ...task, columnId: 'b' });
    await pending;
    await settle();
    stats().flush(data);
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
  });
  it('does not refresh for failed mutation or drag rollback', async () => {
    await loaded();
    const edit = fixture.componentInstance.tasks.update('t', { ...task, title: 'Edited' });
    http.expectOne('/api/tasks/t').flush({}, { status: 500, statusText: 'Failure' });
    await edit;
    await settle();
    const move = fixture.componentInstance.tasks.drop(drop());
    http.expectOne('/api/tasks/t/placement').flush({}, { status: 500, statusText: 'Failure' });
    await move;
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
  });
  it('makes zero statistics requests for Search, Column/Priority/Due filters and Task order', async () => {
    await loaded();
    const view = fixture.componentInstance.taskView;
    view.setColumnFilter('a');
    await settle();
    view.setFilter('HIGH');
    await settle();
    view.setDueDateFilter('OVERDUE');
    await settle();
    view.setOrder('DUE_DATE_ASC');
    await settle();
    view.setSearch('login');
    await settle();
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne((r) => r.url.endsWith('/tasks/search')).flush([task]);
    await settle();
    http.expectNone((r) => r.url.endsWith('/statistics'));
    expect(counts()[0]).toEqual(['Total tasks', '4']);
    expect(counts().slice(2, 5)).toEqual([
      ['High', '2'],
      ['Medium', '1'],
      ['Low', '1'],
    ]);
  });
  it('refreshes only statistics when the shared local civil day changes', async () => {
    await loaded();
    today.set('2026-09-14');
    await settle();
    stats().flush(data);
    await settle();
    http.expectNone(() => true);
  });
});
