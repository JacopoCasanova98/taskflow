import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AUTH_STATE } from './core/routing/auth-state-reader';
import { AuthSessionService } from './features/auth/auth-session.service';
import { routes } from './app.routes';

describe('Application routing', () => {
  const status = signal<'authenticated' | 'anonymous'>('anonymous');
  let originalTitle: string;
  beforeEach(() => {
    status.set('anonymous');
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
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
  it('redirects anonymous root navigation to login with a returnUrl', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2F');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Log in');
  });
  it.each([
    ['/login', 'Log in'],
    ['/register', 'Create account'],
  ])('allows anonymous navigation to %s', async (url, heading) => {
    const harness = await RouterTestingHarness.create(url);
    expect(TestBed.inject(Router).url).toBe(url);
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe(heading);
    expect(TestBed.inject(Title).getTitle()).toBe(heading + ' | TaskFlow');
  });
  it.each(['/', '/login', '/register'])(
    'lands authenticated navigation from %s at the empty root',
    async (url) => {
      status.set('authenticated');
      const harness = await RouterTestingHarness.create(url);
      expect(TestBed.inject(Router).url).toBe('/');
      expect(harness.routeNativeElement).toBeNull();
      expect(TestBed.inject(Title).getTitle()).toBe('TaskFlow');
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
});
