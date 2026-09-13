import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By, Title } from '@angular/platform-browser';
import { NavigationEnd, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { App } from './app';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/config/api-base-url';
import { AuthSessionService } from './features/auth/auth-session.service';
import { AuthenticationResponse } from './features/auth/auth.models';
import { BoardWorkspace } from './features/boards/board-workspace/board-workspace';
import { Board } from './features/boards/board.models';
import { Column } from './features/columns/column.models';
import { Task } from './features/tasks/task.models';
import { TaskDropListData } from './features/tasks/task-placement';

const credentials = { email: 'acceptance@example.com', password: 'Acceptance password 2026!' };
const timestamp = '2026-09-13T10:00:00Z';
const board: Board = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Acceptance Board',
  createdAt: timestamp,
  updatedAt: timestamp,
};
const backlog: Column = {
  id: '22222222-2222-4222-8222-222222222222',
  name: 'Backlog',
  position: 0,
  createdAt: timestamp,
  updatedAt: timestamp,
};
const progress: Column = {
  ...backlog,
  id: '33333333-3333-4333-8333-333333333333',
  name: 'In Progress',
  position: 1,
};
const content = {
  title: 'Fix login flow',
  description: 'Acceptance search target',
  priority: 'HIGH' as const,
  dueDate: '2026-09-14',
};
const task: Task = {
  ...content,
  id: '44444444-4444-4444-8444-444444444444',
  columnId: backlog.id,
  position: 0,
  createdAt: timestamp,
  updatedAt: timestamp,
};
const edited: Task = {
  ...task,
  title: 'Fix authentication flow',
  priority: 'MEDIUM',
  updatedAt: '2026-09-13T10:01:00Z',
};
const moved: Task = { ...edited, columnId: progress.id, updatedAt: '2026-09-13T10:02:00Z' };
const authentication = (accessToken: string): AuthenticationResponse => ({
  user: { id: '55555555-5555-4555-8555-555555555555', email: credentials.email },
  // Opaque test token, as in the existing auth tests; the client does not decode JWTs.
  accessToken,
  accessTokenExpiresAt: '2026-09-13T10:15:00Z',
});

/** Routed functional acceptance with real application code and mocked HTTP, not browser E2E. */
describe('TaskFlow functional acceptance', () => {
  let fixture: ComponentFixture<App>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let router: Router;
  let session: AuthSessionService;
  let originalTitle: string;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 13, 12));
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSessionService);
    router = TestBed.inject(Router);
    originalTitle = TestBed.inject(Title).getTitle();
    // Exercise the production app initializer: CSRF first, then an absent refresh session.
    expect(session.status()).toBe('initializing');
    request('GET', '/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    request('POST', '/api/auth/refresh', null).flush(
      { code: 'SESSION_INVALID' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(session.status()).toBe('anonymous');
    expect(session.initializationUnavailable()).toBe(false);
    fixture = TestBed.createComponent(App);
    element = fixture.nativeElement;
    await router.navigateByUrl('/register');
    await settle();
  });
  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(Title).setTitle(originalTitle);
    vi.useRealTimers();
    http.verify();
  });

  async function settle() {
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    TestBed.tick();
  }
  function request(method: string, url: string, body: object | string | null = null) {
    const pending = http.expectOne(url);
    expect(pending.request.method).toBe(method);
    expect(pending.request.body).toEqual(body);
    expect(pending.request.headers.get('Authorization')).toBe(
      url.startsWith('/api/auth/') ? null : 'Bearer ' + session.accessToken(),
    );
    return pending;
  }
  async function click(label: string, scope: ParentNode = element) {
    const buttons = Array.from(
      scope.querySelectorAll<HTMLButtonElement | HTMLAnchorElement>('button, a'),
    ).filter((node) => (node.getAttribute('aria-label') || node.textContent?.trim()) === label);
    expect(buttons).toHaveLength(1);
    if (buttons[0] instanceof HTMLButtonElement) expect(buttons[0].disabled).toBe(false);
    const navigation = buttons[0] instanceof HTMLAnchorElement ? nextNavigation() : null;
    buttons[0].click();
    if (navigation) await navigation;
    await settle();
  }
  async function fill(selector: string, value: string) {
    const input = element.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
      selector,
    )!;
    expect(input).not.toBeNull();
    input.value = value;
    // Native selection emits input (Signal Forms) followed by change (view controls).
    input.dispatchEvent(new Event('input', { bubbles: true }));
    if (input instanceof HTMLSelectElement)
      input.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
  }
  function workspace() {
    return fixture.debugElement.query(By.directive(BoardWorkspace))
      .componentInstance as BoardWorkspace;
  }
  function canonicalTasks() {
    const state = workspace().state.workspace();
    if (state.status !== 'ready') throw new Error('Expected canonical ready workspace');
    return state.columns.flatMap((lane) => lane.tasks);
  }
  function lane(index: number) {
    return element.querySelectorAll('app-task-list')[index];
  }
  function statisticsRows() {
    return Array.from(element.querySelectorAll('app-board-statistics dl > div'), (row) => [
      row.querySelector('dt')?.textContent,
      row.querySelector('dd')?.textContent,
    ]);
  }
  async function statistics(columns: readonly Column[], current: Task | null = null) {
    request('GET', '/api/boards/' + board.id + '/statistics?asOf=2026-09-13').flush({
      totalTasks: current ? 1 : 0,
      overdueTasks: 0,
      priorityDistribution: {
        low: 0,
        medium: current?.priority === 'MEDIUM' ? 1 : 0,
        high: current?.priority === 'HIGH' ? 1 : 0,
      },
      statusDistribution: columns.map((column) => ({
        columnId: column.id,
        name: column.name,
        position: column.position,
        taskCount: current?.columnId === column.id ? 1 : 0,
      })),
    });
    await settle();
    expect(statisticsRows()).toEqual([
      ['Total tasks', current ? '1' : '0'],
      ['Overdue', '0'],
      ['High', current?.priority === 'HIGH' ? '1' : '0'],
      ['Medium', current?.priority === 'MEDIUM' ? '1' : '0'],
      ['Low', '0'],
      ...columns.map((column) => [column.name, current?.columnId === column.id ? '1' : '0']),
    ]);
    http.expectNone(() => true);
  }
  function nextNavigation() {
    return firstValueFrom(router.events.pipe(filter((event) => event instanceof NavigationEnd)));
  }
  async function logout() {
    await click('Log out', element.querySelector('header')!);
    const navigation = nextNavigation();
    request('POST', '/api/auth/logout', null).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await navigation;
    await settle();
    expect(session.status()).toBe('anonymous');
    expect(session.accessToken()).toBeNull();
    expect(session.user()).toBeNull();
    expect(router.url).toBe('/login');
    expect(element.querySelector('header')?.textContent).not.toContain(credentials.email);
    expect(element.querySelector('h1')?.textContent).toBe('Log in');
  }

  it('registers, logs in, manages a Board and Task across views, deletes, and logs out', async () => {
    expect(element.querySelector('h1')?.textContent).toBe('Create account');
    await fill('#email', credentials.email);
    await fill('#password', credentials.password);
    await click('Create account');
    const registered = nextNavigation();
    request('POST', '/api/auth/register', credentials).flush(
      authentication('registration-access'),
      { status: 201, statusText: 'Created' },
    );
    await registered;
    await settle();
    expect(session.isAuthenticated()).toBe(true);
    expect(session.accessToken()).toBe('registration-access');
    expect(router.url).toBe('/boards');
    request('GET', '/api/boards').flush([]);
    await settle();
    // Registration intentionally signs in. Use real logout before testing real Login.
    await logout();
    await fill('#email', credentials.email);
    await fill('#password', credentials.password);
    await click('Log in');
    const loggedIn = nextNavigation();
    request('POST', '/api/auth/login', credentials).flush(authentication('login-access'));
    await loggedIn;
    await settle();
    expect(session.user()?.email).toBe(credentials.email);
    expect(session.accessToken()).toBe('login-access');
    expect(router.url).toBe('/boards');
    expect(element.querySelector('header')?.textContent).toContain(credentials.email);
    request('GET', '/api/boards').flush([]);
    await settle();
    expect(element.textContent).toContain('No boards yet.');

    await click('Create board');
    await fill('#create-name', board.name);
    await click('Create board');
    request('POST', '/api/boards', { name: board.name }).flush(board, {
      status: 201,
      statusText: 'Created',
    });
    await settle();
    await click(board.name);
    expect(router.url).toBe('/boards/' + board.id);
    request('GET', '/api/boards/' + board.id).flush(board);
    request('GET', '/api/boards/' + board.id + '/columns').flush([]);
    await settle();
    await statistics([]);
    expect(element.querySelector('h1')?.textContent).toBe(board.name);
    expect(element.textContent).toContain('This board has no columns yet.');

    for (const [index, column] of [backlog, progress].entries()) {
      await click('Add column');
      await fill('#create-column-name', column.name);
      await click('Add column');
      http.expectNone((r) => r.url.endsWith('/statistics'));
      request('POST', '/api/boards/' + board.id + '/columns', { name: column.name }).flush(column, {
        status: 201,
        statusText: 'Created',
      });
      await settle();
      await statistics([backlog, progress].slice(0, index + 1));
      expect(element.querySelectorAll('app-column-controls .lane h2')[index]?.textContent).toBe(
        column.name,
      );
      expect(
        Array.from(element.querySelectorAll('#task-column-filter option'), (option) =>
          option.textContent?.trim(),
        ),
      ).toEqual(['All columns', ...[backlog, progress].slice(0, index + 1).map((c) => c.name)]);
    }
    await click('Add task to Backlog');
    await fill('app-task-form input[type="text"]', content.title);
    await fill('textarea', content.description);
    await fill('app-task-form select', content.priority);
    await fill('input[type="date"]', content.dueDate);
    await click('Create task');
    request('POST', '/api/columns/' + backlog.id + '/tasks', content).flush(task, {
      status: 201,
      statusText: 'Created',
    });
    await settle();
    await statistics([backlog, progress], task);
    expect(lane(0).textContent).toContain(task.title);
    expect(lane(0).querySelector('app-task-priority-indicator')?.textContent).toContain('High');
    expect(lane(0).querySelector('time')?.getAttribute('datetime')).toBe(task.dueDate);

    await click('View task: ' + task.title);
    const details = element.querySelector('app-task-details')!;
    expect(details.textContent).toContain(content.title);
    expect(details.textContent).toContain(content.description);
    expect(details.textContent).toContain('High');
    expect(details.querySelector('time')?.getAttribute('datetime')).toBe(task.dueDate);
    http.expectNone(() => true);
    await click('Edit', details);
    await fill('app-task-form input[type="text"]', edited.title);
    await fill('app-task-form select', edited.priority);
    await click('Save task');
    request('PUT', '/api/tasks/' + task.id, {
      ...content,
      title: edited.title,
      priority: edited.priority,
    }).flush(edited);
    await settle();
    await statistics([backlog, progress], edited);
    expect(lane(0).textContent).toContain(edited.title);
    expect(details.querySelector('h3')?.textContent).toBe(edited.title);
    expect(details.textContent).toContain('Medium');
    await click('Close', details);

    // Programmatic typed CDK event exercises the real drop binding, not pointer geometry.
    const lists = fixture.debugElement.queryAll(By.directive(CdkDropList));
    const target = lists[1].injector.get(CdkDropList) as CdkDropList<TaskDropListData>;
    target.dropped.emit({
      previousContainer: lists[0].injector.get(CdkDropList),
      container: target,
      item: fixture.debugElement.query(By.directive(CdkDrag)).injector.get(CdkDrag),
      previousIndex: 0,
      currentIndex: 0,
    } as CdkDragDrop<TaskDropListData, TaskDropListData, string>);
    await settle();
    expect(lane(0).textContent).toContain('No tasks yet.');
    expect(lane(1).textContent).toContain(edited.title);
    http.expectNone((r) => r.url.endsWith('/statistics'));
    request('PUT', '/api/tasks/' + task.id + '/placement', {
      columnId: progress.id,
      position: 0,
    }).flush(moved);
    await settle();
    await statistics([backlog, progress], moved);
    expect(canonicalTasks()).toEqual([moved]);

    const canonicalStatistics = statisticsRows();
    expect(element.querySelector<HTMLSelectElement>('#task-priority-order')?.value).toBe('MANUAL');
    await fill('#task-priority-filter', 'MEDIUM');
    await fill('#task-column-filter', progress.id);
    expect(lane(1).textContent).toContain(moved.title);
    await fill('#task-priority-filter', 'HIGH');
    expect(element.querySelectorAll('.task-title')).toHaveLength(0);
    expect(element.textContent).toContain('No tasks match the current filters.');
    expect(statisticsRows()).toEqual(canonicalStatistics);
    expect(canonicalTasks()).toEqual([moved]);
    await click('Clear filters');
    await fill('#task-due-filter', 'OVERDUE');
    await fill('#task-priority-order', 'DUE_DATE_ASC');
    expect(statisticsRows()).toEqual(canonicalStatistics);
    expect(element.querySelectorAll('.task-title')).toHaveLength(0);
    await click('Clear filters');
    await fill('#task-priority-order', 'MANUAL');
    http.expectNone(() => true);
    for (const [query, matches] of [
      ['authentication', [moved]],
      ['does-not-exist', []],
    ] as const) {
      await fill('#task-search', query);
      await vi.advanceTimersByTimeAsync(299);
      http.expectNone(() => true);
      await vi.advanceTimersByTimeAsync(1);
      request('GET', '/api/boards/' + board.id + '/tasks/search?q=' + query).flush([...matches]);
      await settle();
      expect(statisticsRows()).toEqual(canonicalStatistics);
      http.expectNone(() => true);
      if (matches.length)
        expect(lane(1).querySelector('.task-title')?.textContent?.trim()).toBe(moved.title);
      else expect(element.textContent).toContain('No tasks match your search.');
    }
    await click('Clear search');
    expect(canonicalTasks()).toEqual([moved]);
    expect(lane(1).querySelector('.task-title')?.textContent?.trim()).toBe(moved.title);
    http.expectNone(() => true);

    await click('View task: ' + moved.title);
    await click('Delete', element.querySelector('app-task-details')!);
    http.expectNone(() => true);
    await click('Delete task');
    request('DELETE', '/api/tasks/' + task.id).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await settle();
    await statistics([backlog, progress]);
    expect(canonicalTasks()).toEqual([]);
    expect(element.querySelector('app-task-details')).toBeNull();
    expect(lane(1).textContent).toContain('No tasks yet.');
    await logout();
    expect(element.querySelector('app-board-workspace')).toBeNull();
    await router.navigateByUrl('/boards/' + board.id);
    await settle();
    expect(router.url).toBe('/login?returnUrl=%2Fboards%2F' + board.id);
    expect(element.querySelector('h1')?.textContent).toBe('Log in');
    http.expectNone(() => true);
  });
});
