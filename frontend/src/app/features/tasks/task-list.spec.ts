import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardWorkspace } from '../boards/board-workspace/board-workspace';
import { Task } from './task.models';

const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id.toUpperCase(),
  position,
  createdAt: '',
  updatedAt: '',
}));
const task: Task = {
  id: 'b0',
  columnId: 'b',
  title: 'Fix login',
  description: 'Plain <b>text</b>\nSecond line',
  priority: 'HIGH',
  dueDate: '2026-09-30',
  position: 0,
  createdAt: 'created',
  updatedAt: 'updated',
};
describe('Task CRUD in workspace', () => {
  let fixture: ComponentFixture<BoardWorkspace>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({ boardId: 'one' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    fixture = TestBed.createComponent(BoardWorkspace);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    await load();
  });
  afterEach(() => http.verify());
  async function settle() {
    await Promise.resolve();
    await fixture.whenStable();
  }
  async function load(id = 'one', response: Task[] = [task]) {
    http
      .expectOne('/api/boards/' + id)
      .flush({ id, name: 'Board ' + id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(columns);
    columns.forEach((column) =>
      http
        .expectOne('/api/columns/' + column.id + '/tasks')
        .flush(response.filter((task) => task.columnId === column.id)),
    );
    await settle();
  }
  function button(name: string) {
    const found = Array.from(element.querySelectorAll('button')).find(
      (b) => (b.getAttribute('aria-label') || b.textContent?.trim()) === name,
    );
    expect(found, name).toBeDefined();
    return found!;
  }
  async function click(name: string) {
    button(name).click();
    await settle();
  }
  async function fill(field: string, value: string) {
    const input = element.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
      '[id$="-' + field + '"]',
    )!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }
  async function submit() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
  }
  it('has Add task in every lane, including empty lanes, and one create form at a time', async () => {
    for (const id of ['A', 'B', 'C']) expect(button('Add task to ' + id).disabled).toBe(false);
    expect(element.textContent).toContain('No tasks yet.');
    await click('Add task to B');
    expect(button('Add task to A').disabled).toBe(true);
    expect(element.querySelectorAll('app-task-form')).toHaveLength(1);
    await click('Cancel');
    expect(element.querySelector('form')).toBeNull();
  });
  it('creates in the selected Column with explicit MEDIUM and canonical content', async () => {
    await click('Add task to B');
    await fill('title', '  New task  ');
    await submit();
    const request = http.expectOne('/api/columns/b/tasks');
    expect(request.request.body).toEqual({
      title: 'New task',
      description: null,
      priority: 'MEDIUM',
      dueDate: null,
    });
    request.flush({ ...task, id: 'new', title: 'Canonical', position: 1 });
    await settle();
    expect(element.querySelector('form')).toBeNull();
    expect(button('View task: Canonical')).toBeDefined();
    await click('Add task to B');
    expect(element.querySelector<HTMLInputElement>('[id$="-title"]')?.value).toBe('');
  });
  it('opens details from memory with plain text content and closes without requests', async () => {
    await click('View task: Fix login');
    const details = element.querySelector('app-task-details')!;
    expect(details.querySelector('h3')?.textContent).toBe('Fix login');
    expect(details.textContent).toContain(task.description);
    expect(details.querySelector('b')).toBeNull();
    expect(details.textContent).toContain('Priority: HIGH');
    expect(details.textContent).toContain('Due date: 2026-09-30');
    expect(details.textContent).not.toContain('created');
    expect(details.querySelector('[draggable]')).toBeNull();
    for (const name of ['Edit', 'Delete', 'Close']) expect(button(name)).toBeDefined();
    await click('Close');
    expect(element.querySelector('app-task-details')).toBeNull();
  });
  it('edits all content and immediately shows canonical details without moving the Task', async () => {
    await click('View task: Fix login');
    await click('Edit');
    expect(element.querySelector<HTMLInputElement>('[id$="-title"]')?.value).toBe(task.title);
    expect(element.querySelector('textarea')?.value).toBe(task.description);
    expect(element.querySelector('select')?.value).toBe('HIGH');
    await fill('title', ' Updated ');
    await fill('description', '');
    await fill('priority', 'LOW');
    await fill('dueDate', '');
    await submit();
    const request = http.expectOne('/api/tasks/b0');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      title: 'Updated',
      description: null,
      priority: 'LOW',
      dueDate: null,
    });
    request.flush({
      ...task,
      title: 'Canonical update',
      description: null,
      priority: 'LOW',
      dueDate: null,
    });
    await settle();
    expect(element.querySelector('form')).toBeNull();
    const details = element.querySelector('app-task-details')!;
    expect(details.querySelector('h3')?.textContent).toBe('Canonical update');
    expect(details.textContent).toContain('Priority: LOW');
    expect(details.querySelector('.description')).toBeNull();
    expect(details.textContent).not.toContain('Due date:');
  });
  it('requires named confirmation, allows Cancel, and sends delete once', async () => {
    await click('View task: Fix login');
    await click('Delete');
    http.expectNone('/api/tasks/b0');
    expect(element.textContent).toContain('Delete “Fix login”?');
    expect(element.textContent).toContain('This will permanently delete the task.');
    await click('Cancel');
    http.expectNone('/api/tasks/b0');
    await click('Delete');
    await click('Delete task');
    await click('Delete task');
    http.expectOne('/api/tasks/b0').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(element.querySelector('app-task-details')).toBeNull();
    expect(element.textContent).not.toContain('Fix login');
  });
  it.each(['create', 'update', 'delete'] as const)(
    'retains workspace and usable controls after %s failure',
    async (operation) => {
      if (operation === 'create') {
        await click('Add task to B');
        await fill('title', 'Entered');
        await submit();
      } else {
        await click('View task: Fix login');
        await click(operation === 'update' ? 'Edit' : 'Delete');
        if (operation === 'update') {
          await fill('title', 'Entered');
          await submit();
        } else await click('Delete task');
      }
      http
        .expectOne(operation === 'create' ? '/api/columns/b/tasks' : '/api/tasks/b0')
        .flush({ detail: 'Secret' }, { status: 500, statusText: 'Failure' });
      await settle();
      expect(element.querySelector('h1')?.textContent).toBe('Board one');
      expect(button('View task: Fix login')).toBeDefined();
      expect(element.textContent).toContain("We couldn't " + operation + ' the task.');
      expect(element.textContent).not.toContain('Secret');
      if (operation !== 'delete')
        expect(element.querySelector<HTMLInputElement>('[id$="-title"]')?.value).toBe('Entered');
      else expect(button('Delete task').disabled).toBe(false);
    },
  );
  it.each(['title', 'description', 'priority', 'dueDate', 'unknown'])(
    'maps backend %s validation without exposing server text',
    async (field) => {
      await click('View task: Fix login');
      await click('Edit');
      await fill('title', 'Entered');
      await submit();
      http.expectOne('/api/tasks/b0').flush(
        {
          code: 'VALIDATION_FAILED',
          detail: 'Secret detail',
          violations: [{ field, message: 'Secret message' }],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      await settle();
      expect(element.querySelector('form')).not.toBeNull();
      expect(button('View task: Fix login')).toBeDefined();
      expect(element.textContent).not.toContain('Secret');
      expect(element.textContent).toContain('Check the task fields and try again.');
      if (field !== 'unknown') {
        expect(
          element.querySelector('[id$="-' + field + '-errors"]')?.textContent?.trim(),
        ).not.toBe('');
        expect(element.querySelector('[id$="-' + field + '"]')?.getAttribute('aria-invalid')).toBe(
          'true',
        );
      }
    },
  );
  it('safely handles malformed requests without closing the editor', async () => {
    await click('View task: Fix login');
    await click('Edit');
    await submit();
    http
      .expectOne('/api/tasks/b0')
      .flush(
        { code: 'MALFORMED_REQUEST', detail: 'Secret' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    expect(element.querySelector('form')).not.toBeNull();
    expect(element.textContent).toContain("We couldn't update the task.");
    expect(element.textContent).not.toContain('Secret');
  });
  it('closes stale details and announces TASK_NOT_FOUND while reloading', async () => {
    await click('View task: Fix login');
    await click('Edit');
    await submit();
    http
      .expectOne('/api/tasks/b0')
      .flush({ code: 'TASK_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(element.textContent).toContain('This task is no longer available.');
    expect(element.querySelector('app-task-details')).toBeNull();
    await load('one', []);
    expect(element.querySelector('app-task-details')).toBeNull();
  });
  it('coordinates Task and Column controls while writes are pending', async () => {
    await click('Add task to B');
    await fill('title', 'New');
    await submit();
    for (const name of ['Delete B', 'Rename B', 'Move B left', 'Add column'])
      expect(button(name).disabled).toBe(true);
    http.expectOne('/api/columns/b/tasks').flush({ ...task, id: 'new', position: 1 });
    await settle();
    await click('Move B left');
    expect(button('Add task to B').disabled).toBe(true);
    expect(button('View task: Fix login').disabled).toBe(true);
    http
      .expectOne('/api/boards/one/columns/order')
      .flush([columns[1], columns[0], columns[2]].map((c, position) => ({ ...c, position })));
    await settle();
  });
  it('ignores old form responses after route change and preserves the new form', async () => {
    await click('Add task to B');
    await fill('title', 'Old');
    await submit();
    const old = http.expectOne('/api/columns/b/tasks');
    params.next(convertToParamMap({ boardId: 'two' }));
    await load('two');
    await click('Add task to B');
    await fill('title', 'New draft');
    old.flush({ code: 'COLUMN_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(element.querySelector('h1')?.textContent).toBe('Board two');
    expect(element.querySelector<HTMLInputElement>('[id$="-title"]')?.value).toBe('New draft');
  });
  it('clears a create form when its empty Column is deleted, allowing creation elsewhere', async () => {
    await click('Add task to A');
    await fill('title', 'Unsubmitted');
    await click('Delete A');
    await click('Delete column');
    http.expectOne('/api/columns/a').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(element.querySelector('app-task-form')).toBeNull();
    expect(button('Add task to B').disabled).toBe(false);
    await click('Add task to B');
    expect(element.querySelector<HTMLInputElement>('[id$="-title"]')?.value).toBe('');
  });
});
