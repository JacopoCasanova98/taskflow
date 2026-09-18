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
import { taskOrders, taskOrderLabels } from './task-order';
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
  dueDate: null,
  createdAt: '',
  updatedAt: '',
}));
const low: Task = { ...tasks[0], id: 'E', title: 'E', columnId: 'b', position: 0 };
const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id,
  position,
  createdAt: '',
  updatedAt: '',
}));

describe('Priority view in the Board workspace', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let http: HttpTestingController;
  let element: HTMLElement;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let view: TaskView;
  beforeEach(async () => {
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
  afterEach(() => http.verify());
  async function settle() {
    await Promise.resolve();
    await fixture.whenStable();
  }
  async function load(id = 'one', response = [...tasks, low]) {
    http.expectOne('/api/boards/' + id).flush({ id, name: id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(columns);
    http.expectOne('/api/boards/' + id + '/tasks').flush(response);
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
  it('offers every approved mode with human labels', () => {
    expect(
      Array.from(select('task-priority-order').options, (o) => [o.value, o.textContent?.trim()]),
    ).toEqual(taskOrders.map((order) => [order, taskOrderLabels[order]]));
  });
  it('changes every sort locally per Column and restores canonical Manual with zero HTTP', async () => {
    const before = canonical();
    const snapshot = structuredClone(before);
    for (const order of taskOrders) {
      await change('task-priority-order', order);
      expect(view.order()).toBe(order);
      expect(titles(1)).toBe('E');
      expect(lanes()).toHaveLength(3);
      assertDragDisabled(order !== 'MANUAL');
      expect(canonical()).toBe(before);
    }
    await change('task-priority-order', 'MANUAL');
    expect(titles()).toBe('ABCD');
    expect(canonical()).toEqual(snapshot);
    http.expectNone(() => true);
  });
  it('rejects stale drop callbacks in Created newest without optimistic movement', async () => {
    const event = dropEvent();
    const before = canonical();
    await change('task-priority-order', 'CREATED_NEWEST');
    await fixture.componentInstance.tasks.drop(event);
    expect(canonical()).toBe(before);
    http.expectNone(() => true);
  });
  it.each(['CREATED_NEWEST', 'DUE_DATE_ASC', 'UPDATED_NEWEST'] as const)(
    'projects create, edit and delete responses under %s without placement or reload',
    async (order) => {
      await change('task-priority-order', order);
      const created = {
        ...tasks[0],
        id: 'N',
        title: 'N',
        position: 4,
        dueDate: '2026-09-20',
        createdAt: '2026-09-12T10:00:00Z',
        updatedAt: '2026-09-12T10:00:00Z',
      };
      const creation = fixture.componentInstance.tasks.create('a', created);
      http.expectOne('/api/columns/a/tasks').flush(created);
      await creation;
      await settle();
      expect(titles()).toBe('NABCD');
      expect(canonical().columns[0].tasks[4]).toEqual(created);
      await click('View task: B');
      const updated = { ...tasks[1], dueDate: '2026-09-10', updatedAt: '2026-09-12T13:00:00Z' };
      const update = fixture.componentInstance.tasks.update('B', updated);
      http.expectOne('/api/tasks/B').flush(updated);
      await update;
      await settle();
      expect(titles()).toBe(order === 'CREATED_NEWEST' ? 'NABCD' : 'BNACD');
      expect(canonical().columns[0].tasks[1]).toEqual(updated);
      expect(fixture.componentInstance.tasks.selected()).toBe(canonical().columns[0].tasks[1]);
      const deletion = fixture.componentInstance.tasks.delete('N');
      http.expectOne('/api/tasks/N').flush(null, { status: 204, statusText: 'No Content' });
      await deletion;
      await settle();
      expect(titles()).toBe(order === 'CREATED_NEWEST' ? 'ABCD' : 'BACD');
      http.expectNone(() => true);
    },
  );
  it.each(['create', 'edit'])(
    'preserves unsent %s form and canonical details through sorting',
    async (kind) => {
      if (kind === 'create') await click('Add task to a');
      else {
        await click('View task: A');
        await click('Edit');
      }
      const selected = fixture.componentInstance.tasks.selected();
      const form = element.querySelector('form');
      const input = element.querySelector<HTMLInputElement>('form input')!;
      input.value = 'Unsent draft';
      input.dispatchEvent(new Event('input', { bubbles: true }));
      await settle();
      for (const order of taskOrders) await change('task-priority-order', order);
      expect(element.querySelector('form')).toBe(form);
      expect(input.value).toBe('Unsent draft');
      expect(fixture.componentInstance.tasks.selected()).toBe(selected);
    },
  );
  it('preserves Updated newest on Clear filters and reload but resets on Board change', async () => {
    await change('task-priority-order', 'UPDATED_NEWEST');
    await change('task-priority-filter', 'HIGH');
    await click('Clear filters');
    expect(view.order()).toBe('UPDATED_NEWEST');
    fixture.componentInstance.state.retry();
    await load(
      'one',
      tasks.map((t) => (t.id === 'B' ? { ...t, updatedAt: '2026-09-12T13:00:00Z' } : t)),
    );
    expect(view.order()).toBe('UPDATED_NEWEST');
    expect(titles()).toBe('BACD');
    params.next(convertToParamMap({ boardId: 'two' }));
    await load('two');
    expect(view.order()).toBe('MANUAL');
    expect(titles()).toBe('ABCD');
  });
  it('defaults to labeled native controls and shows a textual indicator on every card', () => {
    expect(select('task-priority-filter').value).toBe('ALL');
    expect(select('task-priority-order').value).toBe('MANUAL');
    expect(element.querySelector('label[for="task-priority-filter"]')?.textContent).toBe(
      'Priority',
    );
    expect(element.querySelector('label[for="task-priority-order"]')?.textContent).toBe(
      'Task order',
    );
    expect(element.querySelectorAll('li app-task-priority-indicator')).toHaveLength(5);
    expect(element.querySelector('#task-movement-guidance')).toBeNull();
    expect(titles()).toBe('ABCD');
    assertDragDisabled(false);
  });
  it('filters independently without hiding Columns or modifying canonical state', async () => {
    const before = canonical();
    await change('task-priority-filter', 'HIGH');
    expect(titles()).toBe('BD');
    expect(titles(1)).toBe('');
    expect(lanes()).toHaveLength(3);
    expect(lanes()[1].textContent).toContain('No tasks match the current filters.');
    expect(lanes()[1].textContent).not.toContain('No tasks yet.');
    expect(lanes()[2].textContent).toContain('No tasks yet.');
    expect(canonical()).toBe(before);
    await change('task-priority-filter', 'ALL');
    expect(titles()).toBe('ABCD');
    expect(titles(1)).toBe('E');
    expect(canonical()).toBe(before);
  });
  it('sorts both directions and restores exact manual order without HTTP or position changes', async () => {
    const before = canonical();
    const snapshot = structuredClone(before);
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    expect(titles()).toBe('BDCA');
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    expect(titles()).toBe('ACBD');
    await change('task-priority-order', 'MANUAL');
    expect(titles()).toBe('ABCD');
    expect(canonical()).toBe(before);
    expect(canonical()).toEqual(snapshot);
  });
  it('enforces both projection drag guards and explains how to restore movement', async () => {
    const before = canonical();
    const event = dropEvent();
    await change('task-priority-filter', 'HIGH');
    assertDragDisabled(true);
    expect(element.querySelector('#task-movement-guidance')?.textContent).toContain(
      'manual order with search and all filters cleared',
    );
    await fixture.componentInstance.tasks.drop(event);
    await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
    assertDragDisabled(true);
    await change('task-priority-filter', 'ALL');
    assertDragDisabled(true);
    await fixture.componentInstance.tasks.drop(event);
    await change('task-priority-filter', 'HIGH');
    await change('task-priority-order', 'MANUAL');
    assertDragDisabled(true);
    await fixture.componentInstance.tasks.drop(event);
    await change('task-priority-filter', 'ALL');
    assertDragDisabled(false);
    expect(element.querySelector('#task-movement-guidance')).toBeNull();
    expect(canonical()).toBe(before);
  });
  it('keeps hidden selected details and unsent edit drafts while controls change', async () => {
    await click('View task: A');
    await click('Edit');
    const form = element.querySelector('form');
    const input = element.querySelector<HTMLInputElement>('[id$="-title"]')!;
    input.value = 'Unsent';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    await change('task-priority-filter', 'HIGH');
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    expect(titles()).toBe('BD');
    expect(element.querySelector('form')).toBe(form);
    expect(input.value).toBe('Unsent');
    expect(fixture.componentInstance.tasks.selected()?.id).toBe('A');
  });
  it('reprojects a canonical HIGH-to-LOW edit without placement or reload and keeps details coherent', async () => {
    await change('task-priority-filter', 'HIGH');
    await click('View task: B');
    await click('Edit');
    const priority = element.querySelector<HTMLSelectElement>('form select')!;
    priority.value = 'LOW';
    priority.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    await submit();
    const request = http.expectOne('/api/tasks/B');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.priority).toBe('LOW');
    request.flush({ ...tasks[1], priority: 'LOW' });
    await settle();
    expect(titles()).toBe('D');
    expect(canonical().columns[0].tasks[1]).toEqual({ ...tasks[1], priority: 'LOW' });
    expect(element.querySelector('app-task-details')?.textContent).toContain('Priority: Low');
    expect(select('task-priority-filter').value).toBe('HIGH');
  });
  it.each(['LOW', 'HIGH'] as const)(
    'keeps created %s Task canonical under HIGH filtering',
    async (priority) => {
      await change('task-priority-filter', 'HIGH');
      await change('task-priority-order', 'PRIORITY_HIGH_TO_LOW');
      await click('Add task to a');
      const title = element.querySelector<HTMLInputElement>('form input')!;
      title.value = 'New';
      title.dispatchEvent(new Event('input', { bubbles: true }));
      const prioritySelect = element.querySelector<HTMLSelectElement>('form select')!;
      expect(prioritySelect.value).toBe('MEDIUM');
      expect(Array.from(prioritySelect.options, (o) => [o.value, o.textContent?.trim()])).toEqual([
        ['LOW', 'Low'],
        ['MEDIUM', 'Medium'],
        ['HIGH', 'High'],
      ]);
      prioritySelect.value = priority;
      prioritySelect.dispatchEvent(new Event('input', { bubbles: true }));
      await settle();
      await submit();
      const created = { ...tasks[0], id: 'new', title: 'New', priority, position: 4 };
      const request = http.expectOne('/api/columns/a/tasks');
      expect(request.request.body.priority).toBe(priority);
      request.flush(created);
      await settle();
      expect(canonical().columns[0].tasks[4]).toEqual(created);
      expect(titles()).toBe(priority === 'HIGH' ? 'BDNew' : 'BD');
      expect(select('task-priority-filter').value).toBe('HIGH');
      expect(select('task-priority-order').value).toBe('PRIORITY_HIGH_TO_LOW');
      await change('task-priority-filter', 'ALL');
      await change('task-priority-order', 'MANUAL');
      expect(titles()).toBe('ABCDNew');
    },
  );
  it('deletes a visible filtered Task only on canonical success', async () => {
    await change('task-priority-filter', 'HIGH');
    await click('View task: B');
    await click('Delete');
    await click('Delete task');
    expect(titles()).toBe('BD');
    http.expectOne('/api/tasks/B').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(titles()).toBe('D');
    expect(canonical().columns[0].tasks.map((t) => t.id)).toEqual(['A', 'C', 'D']);
    expect(select('task-priority-filter').value).toBe('HIGH');
  });
  it('resets both view controls when navigating to another Board', async () => {
    await change('task-priority-filter', 'HIGH');
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    params.next(convertToParamMap({ boardId: 'two' }));
    await load('two');
    expect(select('task-priority-filter').value).toBe('ALL');
    expect(select('task-priority-order').value).toBe('MANUAL');
    expect(titles()).toBe('ABCD');
    assertDragDisabled(false);
  });
  it('preserves view settings on same-Board reload and applies them to refreshed data', async () => {
    await change('task-priority-filter', 'HIGH');
    await change('task-priority-order', 'PRIORITY_LOW_TO_HIGH');
    fixture.componentInstance.state.retry();
    await load(
      'one',
      tasks.map((t) => (t.id === 'A' ? { ...t, priority: 'HIGH' } : t)),
    );
    expect(select('task-priority-filter').value).toBe('HIGH');
    expect(select('task-priority-order').value).toBe('PRIORITY_LOW_TO_HIGH');
    expect(titles()).toBe('ABD');
    assertDragDisabled(true);
    params.next(convertToParamMap({ boardId: 'one' }));
    await settle();
    expect(view.filter()).toBe('HIGH');
  });
  it('continues to honor the shared write lock after returning to canonical view', async () => {
    await change('task-priority-filter', 'HIGH');
    await click('View task: B');
    await click('Edit');
    await submit();
    await change('task-priority-filter', 'ALL');
    assertDragDisabled(true);
    http.expectOne('/api/tasks/B').flush(tasks[1]);
    await settle();
    assertDragDisabled(false);
  });
  it('ignores unsupported view values', () => {
    view.setFilter('EXTERNAL');
    view.setOrder('TITLE');
    expect(view.filter()).toBe('ALL');
    expect(view.order()).toBe('MANUAL');
  });
});
