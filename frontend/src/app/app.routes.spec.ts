import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AUTH_STATE } from './core/routing/auth-state-reader';
import { AuthSessionService } from './features/auth/auth-session.service';
import { routes } from './app.routes';
import { of } from 'rxjs';
import { BoardApi } from './features/boards/board-api';

describe('Application routing', () => {
  const status = signal<'authenticated' | 'anonymous'>('anonymous');
  let originalTitle: string;
  const listBoards = vi.fn(() => of([]));
  beforeEach(() => {
    status.set('anonymous');
    listBoards.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: BoardApi, useValue: { listBoards } },
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
  it.each(['anonymous', 'authenticated'] as const)(
    'does not introduce workspace routes when %s',
    async (state) => {
      status.set(state);
      const harness = await RouterTestingHarness.create(
        '/boards/49a8b874-b795-42b0-80db-ed1c384c8301',
      );
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Page not found');
      expect(listBoards).not.toHaveBeenCalled();
    },
  );
});
