import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { BoardWorkspace } from '../boards/board-workspace/board-workspace';

const columns = ['a', 'b', 'c'].map((id, position) => ({
  id,
  name: id.toUpperCase(),
  position,
  createdAt: '',
  updatedAt: '',
}));
describe('Column controls in Board workspace', () => {
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
  });
  afterEach(() => http.verify());
  async function settle() {
    // Resolve firstValueFrom before Angular schedules rendering of changed signals.
    await Promise.resolve();
    await fixture.whenStable();
  }
  async function load(response = columns, id = 'one') {
    http
      .expectOne('/api/boards/' + id)
      .flush({ id, name: 'Board ' + id, createdAt: '', updatedAt: '' });
    http.expectOne('/api/boards/' + id + '/columns').flush(response);
    response.forEach((column) =>
      http
        .expectOne('/api/columns/' + column.id + '/tasks')
        .flush([{ id: 'task-' + column.id, title: 'Task ' + column.id }]),
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
  async function fill(value: string) {
    const input = element.querySelector('input')!;
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
  it('creates the first Column and resets its form after success', async () => {
    await load([]);
    await click('Add column');
    expect(element.querySelector('label')?.textContent).toBe('Column name');
    await fill('  First  ');
    await submit();
    const request = http.expectOne('/api/boards/one/columns');
    expect(request.request.body).toEqual({ name: 'First' });
    request.flush({ ...columns[0], name: 'Canonical' });
    await settle();
    expect(element.querySelector('h2')?.textContent).toBe('Canonical');
    expect(element.querySelector('form')).toBeNull();
    await click('Add column');
    expect(element.querySelector('input')?.value).toBe('');
  });
  it('renames inline from current name while retaining Task summaries', async () => {
    await load();
    await click('Rename B');
    expect(element.querySelector('input')?.value).toBe('B');
    expect(element.textContent).toContain('Task b');
    await fill('New');
    await submit();
    http.expectOne('/api/columns/b').flush({ ...columns[1], name: 'Canonical' });
    await settle();
    expect(Array.from(element.querySelectorAll('.lane h2'), (h) => h.textContent)).toEqual([
      'A',
      'Canonical',
      'C',
    ]);
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('[draggable]')).toBeNull();
    expect(element.textContent).toContain('Add task');
    expect(element.querySelector('li button')?.getAttribute('aria-label')).toContain('View task:');
  });
  it('requires confirmation, permits cancellation, and submits deletion only once', async () => {
    await load();
    await click('Delete B');
    http.expectNone('/api/columns/b');
    expect(element.textContent).toContain('Delete “B”?');
    await click('Cancel');
    http.expectNone('/api/columns/b');
    await click('Delete B');
    await click('Delete column');
    await click('Delete column');
    http.expectOne('/api/columns/b').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(Array.from(element.querySelectorAll('.lane h2'), (h) => h.textContent)).toEqual([
      'A',
      'C',
    ]);
    expect(element.textContent).not.toContain('Task b');
  });
  it('allows confirmed deletion with local Tasks and displays COLUMN_NOT_EMPTY safely', async () => {
    await load();
    expect(button('Delete B').disabled).toBe(false);
    await click('Delete B');
    await click('Delete column');
    http
      .expectOne('/api/columns/b')
      .flush(
        { code: 'COLUMN_NOT_EMPTY', detail: 'Secret' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();
    expect(element.textContent).toContain('This column must be empty before it can be deleted.');
    expect(element.textContent).toContain('Task b');
    expect(element.textContent).not.toContain('Secret');
    expect(button('Delete column').disabled).toBe(false);
  });
  it('uses native boundary move buttons and waits for canonical order', async () => {
    await load();
    expect(button('Move A left').disabled).toBe(true);
    expect(button('Move C right').disabled).toBe(true);
    await click('Move B left');
    expect(button('Move B right').disabled).toBe(true);
    expect(Array.from(element.querySelectorAll('.lane h2'), (h) => h.textContent)).toEqual([
      'A',
      'B',
      'C',
    ]);
    const request = http.expectOne('/api/boards/one/columns/order');
    expect(request.request.body).toEqual({ columnIds: ['b', 'a', 'c'] });
    request.flush([columns[1], columns[0], columns[2]].map((c, position) => ({ ...c, position })));
    await settle();
    expect(Array.from(element.querySelectorAll('.lane h2'), (h) => h.textContent)).toEqual([
      'B',
      'A',
      'C',
    ]);
  });
  it('disables both move directions for one Column', async () => {
    await load([columns[0]]);
    expect(button('Move A left').disabled).toBe(true);
    expect(button('Move A right').disabled).toBe(true);
  });
  it.each(['create', 'rename', 'delete', 'reorder'] as const)(
    'keeps the workspace visible with safe %s failure',
    async (operation) => {
      await load();
      if (operation === 'create' || operation === 'rename') {
        await click(operation === 'create' ? 'Add column' : 'Rename B');
        await fill('Entered name');
        await submit();
      } else if (operation === 'delete') {
        await click('Delete B');
        await click('Delete column');
      } else await click('Move B left');
      http
        .expectOne(
          operation === 'create'
            ? '/api/boards/one/columns'
            : operation === 'reorder'
              ? '/api/boards/one/columns/order'
              : '/api/columns/b',
        )
        .flush({ detail: 'Secret' }, { status: 500, statusText: 'Failure' });
      await settle();
      expect(element.querySelector('h1')?.textContent).toBe('Board one');
      expect(element.textContent).toContain("We couldn't " + operation);
      expect(element.textContent).not.toContain("We couldn't load");
      expect(element.textContent).not.toContain('Secret');
      if (operation === 'create' || operation === 'rename')
        expect(element.querySelector('input')?.value).toBe('Entered name');
    },
  );
  it('drops old editors and confirmation errors on route change', async () => {
    await load();
    await click('Delete B');
    await click('Delete column');
    const old = http.expectOne('/api/columns/b');
    params.next(convertToParamMap({ boardId: 'two' }));
    await load(columns, 'two');
    old.flush({ code: 'COLUMN_NOT_EMPTY' }, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(element.querySelector('h1')?.textContent).toBe('Board two');
    expect(element.textContent).not.toContain('must be empty');
    expect(element.querySelector('.confirmation')).toBeNull();
  });
});
