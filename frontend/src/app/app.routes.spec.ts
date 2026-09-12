import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AUTH_STATE } from './core/routing/auth-state-reader';
import { AuthSessionService } from './features/auth/auth-session.service';
import { routes } from './app.routes';
import { of } from 'rxjs';
import { ColumnApi } from './features/columns/column-api';
import { TaskApi } from './features/tasks/task-api';
import { BoardWorkspace } from './features/boards/board-workspace/board-workspace';
import { Subject } from 'rxjs';
import { Board } from './features/boards/board.models';
import { BoardApi } from './features/boards/board-api';

describe('Application routing', () => {
  const status = signal<'authenticated' | 'anonymous'>('anonymous');
  let originalTitle: string;
  const listBoards = vi.fn(() => of([]));
  const getBoard = vi.fn((id: string) => of({ id, name: id, createdAt: '', updatedAt: '' }));
  beforeEach(() => {
    getBoard.mockClear();
    status.set('anonymous');
    listBoards.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: ColumnApi, useValue: { listColumns: () => of([]) } },
        { provide: TaskApi, useValue: { listTasks: vi.fn() } },
        { provide: BoardApi, useValue: { listBoards, getBoard } },
        {
          provide: AUTH_STATE,
          useValue: {
            status: status.asReadonly(),
            isAuthenticated: computed(() => status() === 'authenticated'),
          },
        },
        { provide: AuthSessionService, useValue: {} },
      ],
    });
    originalTitle = TestBed.inject(Title).getTitle();
  });
  afterEach(() => TestBed.inject(Title).setTitle(originalTitle));
  it.each(['/', '/boards'])(
    'redirects anonymous %s to login with a boards returnUrl',
    async (url) => {
      const harness = await RouterTestingHarness.create(url);
      expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fboards');
      expect(listBoards).not.toHaveBeenCalled();
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Log in');
    },
  );
  it.each([
    ['/login', 'Log in'],
    ['/register', 'Create account'],
  ])('allows anonymous navigation to %s', async (url, heading) => {
    const harness = await RouterTestingHarness.create(url);
    expect(TestBed.inject(Router).url).toBe(url);
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(heading);
    expect(TestBed.inject(Title).getTitle()).toBe(heading + ' | TaskFlow');
  });
  it.each(['/', '/boards', '/login', '/register'])(
    'lands authenticated navigation from %s at the Board list',
    async (url) => {
      status.set('authenticated');
      const harness = await RouterTestingHarness.create(url);
      expect(TestBed.inject(Router).url).toBe('/boards');
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Boards');
      expect(TestBed.inject(Title).getTitle()).toBe('Boards | TaskFlow');
      expect(listBoards).toHaveBeenCalledTimes(1);
    },
  );
  it.each(['anonymous', 'authenticated'] as const)(
    'preserves wildcard Not Found when %s',
    async (state) => {
      status.set(state);
      const harness = await RouterTestingHarness.create('/unknown/nested-page');
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Page not found');
      expect(TestBed.inject(Title).getTitle()).toBe('Page not found | TaskFlow');
    },
  );
  it('redirects anonymous workspace access with a safe returnUrl', async () => {
    await RouterTestingHarness.create('/boards/one');
    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fboards%2Fone');
    expect(getBoard).not.toHaveBeenCalled();
  });
  it('loads the authenticated workspace and its title without listing Boards', async () => {
    status.set('authenticated');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/boards/one', BoardWorkspace);
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('one');
    expect(TestBed.inject(Title).getTitle()).toBe('Board | TaskFlow');
    expect(getBoard).toHaveBeenCalledExactlyOnceWith('one');
    expect(listBoards).not.toHaveBeenCalled();
  });
  it('reacts to reused route parameters and ignores a late prior response', async () => {
    status.set('authenticated');
    const pending = new Subject<Board>();
    getBoard.mockReturnValueOnce(pending);
    const harness = await RouterTestingHarness.create();
    const first = await harness.navigateByUrl('/boards/one', BoardWorkspace);
    expect(harness.routeNativeElement?.textContent).toContain('Loading board…');
    const second = await harness.navigateByUrl('/boards/two', BoardWorkspace);
    expect(second).toBe(first);
    pending.next({ id: 'one', name: 'Stale', createdAt: '', updatedAt: '' });
    pending.complete();
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('two');
    expect(getBoard.mock.calls.map(([id]) => id)).toEqual(['one', 'two']);
  });
});
