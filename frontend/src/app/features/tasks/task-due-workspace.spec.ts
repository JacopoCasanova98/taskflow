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

const tasks: Task[] = (['LOW', 'HIGH', 'MEDIUM', 'HIGH'] as const).map((priority, position) => ({
  id: 'ABCD'[position],
  columnId: 'a',
  title: 'ABCD'[position],
  description: null,
  priority,
  position,
  dueDate: [null, '2026-09-11', '2026-09-12', '2026-09-13'][position],
  createdAt: '',
  updatedAt: '',
}));
const low: Task = {
  ...tasks[0],
  id: 'E',
  title: 'E',
  columnId: 'b',
  position: 0,
  dueDate: '2026-09-13',
};
const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: '',
  updatedAt: '',
}));

describe('Due dates in the Board workspace', () => {
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
  async function load(id = 'one', response = [...tasks, low]) {
    http.expectOne('/api/boards/' + id).flush({ id, name: id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(columns);
    for (const column of columns)
      http
        .expectOne('/api/columns/' + column.id + '/tasks')
        .flush(response.filter((task) => task.columnId === column.id));
    await settle();
  }
  function canonical() {
    const state = fixture.componentInstance.state.workspace();
    if (state.status !== 'ready') throw new Error('Expected ready workspace');
    return state;
  }
  function lanes() {
    return element.querySelectorAll('app-task-list');
  }
  function titles(index = 0) {
    return Array.from(lanes()[index].querySelectorAll('.task-title'), (b) =>
      b.textContent?.trim(),
    ).join('');
  }
  function select(id: string) {
    return element.querySelector<HTMLSelectElement>('#' + id)!;
  }
  async function change(id: string, value: string) {
    const control = select(id);
    control.value = value;
    control.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
  }
  async function click(name: string) {
    const button = Array.from(element.querySelectorAll('button')).find(
      (b) => (b.getAttribute('aria-label') || b.textContent?.trim()) === name,
    )!;
    expect(button, name).toBeDefined();
    button.click();
    await settle();
  }
  async function submit() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
  }
  function assertDragDisabled(disabled: boolean) {
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDrag)))
      expect(node.injector.get(CdkDrag).disabled).toBe(disabled);
    for (const node of fixture.debugElement.queryAll(By.directive(CdkDropList)))
      expect(node.injector.get(CdkDropList).disabled).toBe(disabled);
  }
  function dropEvent() {
    const lists = fixture.debugElement.queryAll(By.directive(CdkDropList));
    const container = lists[0].injector.get(CdkDropList) as CdkDropList<TaskDropListData>;
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

  it('shows accessible due metadata while preserving Priority, and no chip for no date', () => {
    expect(select('task-due-filter').value).toBe('ALL');
    expect(element.querySelector('label[for="task-due-filter"]')?.textContent).toBe('Due date');
    expect(Array.from(select('task-due-filter').options, (o) => o.textContent?.trim())).toEqual([
      'All due dates',
      'Overdue',
      'Due today',
      'Upcoming',
      'No due date',
    ]);
    const cards = lanes()[0].querySelectorAll('li');
    expect(cards[0].querySelector('app-task-due-indicator')).toBeNull();
    expect(cards[1].textContent).toContain('Overdue');
    expect(cards[2].textContent).toContain('Due today');
    expect(cards[3].querySelector('time')?.getAttribute('datetime')).toBe('2026-09-13');
    expect(element.querySelectorAll('li app-task-priority-indicator')).toHaveLength(5);
  });
  it.each([
    ['OVERDUE', 'B'],
    ['DUE_TODAY', 'C'],
    ['UPCOMING', 'D'],
    ['NO_DUE_DATE', 'A'],
  ])('projects %s as %s without canonical changes or HTTP', async (filter, expected) => {
    const before = canonical();
    const snapshot = structuredClone(before);
    await change('task-due-filter', filter);
    expect(titles()).toBe(expected);
    expect(canonical()).toBe(before);
    expect(canonical()).toEqual(snapshot);
    await change('task-due-filter', 'ALL');
    expect(titles()).toBe('ABCD');
    expect(canonical()).toBe(before);
  });
  it('combines Priority and Due filters while retaining Priority sort', async () => {
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    await change('task-priority-filter', 'HIGH');
    await change('task-due-filter', 'OVERDUE');
    expect(select('task-priority-filter').value).toBe('HIGH');
    expect(titles()).toBe('B');
    expect(select('task-priority-order').value).toBe('PRIORITY_HIGH_TO_LOW');
    await change('task-priority-filter', 'LOW');
    expect(select('task-due-filter').value).toBe('OVERDUE');
    expect(titles()).toBe('');
    await change('task-due-filter', 'ALL');
    expect(select('task-priority-filter').value).toBe('LOW');
    expect(titles()).toBe('A');
  });
  it('sorts overdue Tasks by Priority with stable manual ties without changing state', async () => {
    fixture.componentInstance.state.retry();
    await load(
      'one',
      tasks.map((t) => ({ ...t, dueDate: '2026-09-11' })),
    );
    const before = canonical();
    await change('task-due-filter', 'OVERDUE');
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    expect(titles()).toBe('BDCA');
    expect(canonical()).toBe(before);
  });
  it('guards due-filtered drops and restores dragging only in canonical view', async () => {
    const before = canonical();
    const event = dropEvent();
    await change('task-due-filter', 'OVERDUE');
    assertDragDisabled(true);
    await fixture.componentInstance.tasks.drop(event);
    expect(canonical()).toBe(before);
    expect(element.querySelector('#task-movement-guidance')?.textContent).toContain(
      'all filters cleared',
    );
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    await change('task-due-filter', 'ALL');
    assertDragDisabled(true);
    await change('task-priority-order', 'MANUAL');
    assertDragDisabled(false);
    expect(element.querySelector('#task-movement-guidance')).toBeNull();
  });
  it('distinguishes filtered-empty from canonical-empty Columns', async () => {
    await change('task-due-filter', 'OVERDUE');
    expect(lanes()[1].textContent).toContain('No tasks match the current filters.');
    expect(lanes()[1].textContent).not.toContain('No tasks yet.');
    expect(lanes()[2].textContent).toContain('No tasks yet.');
    await change('task-due-filter', 'ALL');
    expect(titles(1)).toBe('E');
  });
  it('keeps details and an unsent draft open when due filtering hides its card', async () => {
    await click('View task: A');
    await click('Edit');
    const form = element.querySelector('form');
    const title = element.querySelector<HTMLInputElement>('form input')!;
    title.value = 'Draft';
    title.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    await change('task-due-filter', 'OVERDUE');
    expect(titles()).toBe('B');
    expect(element.querySelector('form')).toBe(form);
    expect(title.value).toBe('Draft');
    expect(fixture.componentInstance.tasks.selected()?.id).toBe('A');
  });
  it.each([
    ['OVERDUE', 'B', '2026-09-13'],
    ['NO_DUE_DATE', 'A', '2026-09-13'],
    ['UPCOMING', 'D', ''],
  ])(
    'reprojects canonical edits under %s, including setting/clearing dates',
    async (filter, id, date) => {
      await change('task-due-filter', filter);
      await click('View task: ' + id);
      await click('Edit');
      const input = element.querySelector<HTMLInputElement>('input[type="date"]')!;
      input.value = date;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      await settle();
      await submit();
      const request = http.expectOne('/api/tasks/' + id);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.dueDate).toBe(date || null);
      const updated = { ...tasks.find((t) => t.id === id)!, dueDate: date || null };
      request.flush(updated);
      await settle();
      expect(titles()).toBe('');
      expect(canonical().columns[0].tasks.find((t) => t.id === id)).toEqual(updated);
      const details = element.querySelector('app-task-details')!;
      if (date) expect(details.querySelector('time')?.getAttribute('datetime')).toBe(date);
      else expect(details.textContent).toContain('No due date');
      expect(select('task-due-filter').value).toBe(filter);
    },
  );
  it.each(['2026-09-11', '2026-09-13', ''])(
    'creates date %s canonically while retaining the due filter',
    async (date) => {
      await change('task-due-filter', 'OVERDUE');
      await click('Add task to a');
      const title = element.querySelector<HTMLInputElement>('form input')!;
      title.value = 'New';
      title.dispatchEvent(new Event('input', { bubbles: true }));
      const input = element.querySelector<HTMLInputElement>('input[type="date"]')!;
      input.value = date;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      await settle();
      await submit();
      const request = http.expectOne('/api/columns/a/tasks');
      expect(request.request.body.dueDate).toBe(date || null);
      const created = { ...tasks[0], id: 'new', title: 'New', dueDate: date || null, position: 4 };
      request.flush(created);
      await settle();
      expect(canonical().columns[0].tasks[4]).toEqual(created);
      expect(titles()).toBe(date === '2026-09-11' ? 'BNew' : 'B');
      expect(select('task-due-filter').value).toBe('OVERDUE');
      await change('task-due-filter', 'ALL');
      expect(titles()).toBe('ABCDNew');
    },
  );
  it('recomputes filtered cards and open details across midnight without HTTP or mutation', async () => {
    await click('View task: C');
    await change('task-due-filter', 'OVERDUE');
    const before = canonical();
    expect(titles()).toBe('B');
    expect(element.querySelector('app-task-details')?.textContent).toContain('Due today');
    vi.advanceTimersByTime(1000);
    await settle();
    expect(titles()).toBe('BC');
    expect(element.querySelector('app-task-details')?.textContent).toContain('Overdue');
    expect(canonical()).toBe(before);
  });
  it('preserves the due filter/order on same-Board reload and resets them on route change', async () => {
    await change('task-due-filter', 'OVERDUE');
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    fixture.componentInstance.state.retry();
    await load();
    expect(select('task-due-filter').value).toBe('OVERDUE');
    expect(select('task-priority-order').value).toBe('PRIORITY_LOW_TO_HIGH');
    params.next(convertToParamMap({ boardId: 'two' }));
    await load('two');
    expect(select('task-priority-filter').value).toBe('ALL');
    expect(select('task-due-filter').value).toBe('ALL');
    expect(select('task-priority-order').value).toBe('MANUAL');
    assertDragDisabled(false);
  });
});
