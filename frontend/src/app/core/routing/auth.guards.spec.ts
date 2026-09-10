import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AUTH_STATE, AuthStateReader } from './auth-state-reader';
import { anonymousOnlyGuard, authenticatedGuard } from './auth.guards';

describe('Functional auth guards', () => {
  const status = signal<'initializing' | 'authenticated' | 'anonymous'>('anonymous');
  const isAuthenticated = signal(false);
  beforeEach(() => {
    status.set('anonymous');
    isAuthenticated.set(false);
    // No HTTP or session mutation provider: guards can only read this narrow contract.
    const reader: AuthStateReader = {
      status: status.asReadonly(),
      isAuthenticated: isAuthenticated.asReadonly(),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AUTH_STATE, useValue: reader }],
    });
  });
  function run(guard: typeof authenticatedGuard, url = '/') {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }
  it('allows authenticated application navigation', () => {
    status.set('authenticated');
    isAuthenticated.set(true);
    expect(run(authenticatedGuard)).toBe(true);
  });
  it('redirects anonymous navigation preserving the original path, query and fragment', () => {
    const url = '/boards/123?filter=open#task';
    const result = run(authenticatedGuard, url) as UrlTree;
    expect(result instanceof UrlTree).toBe(true);
    expect(result.queryParams['returnUrl']).toBe(url);
    expect(TestBed.inject(Router).serializeUrl(result)).toBe(
      '/login?returnUrl=%2Fboards%2F123%3Ffilter%3Dopen%23task',
    );
    expect(status()).toBe('anonymous');
  });
  it('allows anonymous auth pages', () => expect(run(anonymousOnlyGuard)).toBe(true));
  it('redirects authenticated users from auth pages to root without changing state', () => {
    status.set('authenticated');
    isAuthenticated.set(true);
    expect(TestBed.inject(Router).serializeUrl(run(anonymousOnlyGuard) as UrlTree)).toBe('/');
    expect(status()).toBe('authenticated');
  });
  it('cancels unresolved initialization deterministically', () => {
    status.set('initializing');
    expect(run(authenticatedGuard)).toBe(false);
    expect(run(anonymousOnlyGuard)).toBe(false);
    expect(status()).toBe('initializing');
  });
});
