import { signal } from '@angular/core';
import { AUTH_STATE } from './core/routing/auth-state-reader';
import { AuthSessionService } from './features/auth/auth-session.service';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { of } from 'rxjs';
import { BoardApi } from './features/boards/board-api';

describe('App', () => {
  const authenticated = signal(true);
  beforeEach(async () => {
    authenticated.set(true);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        { provide: BoardApi, useValue: { listBoards: () => of([]) } },
        {
          provide: AUTH_STATE,
          useValue: { status: signal('authenticated'), isAuthenticated: authenticated },
        },
        {
          provide: AuthSessionService,
          useValue: {
            isAuthenticated: authenticated,
            user: signal({ email: 'person@example.com' }),
          },
        },
      ],
    }).compileComponents();
  });

  it('identifies TaskFlow and provides a skip link to the main content landmark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const main = element.querySelector('main');
    const skipLink = element.querySelector('a');

    expect(element.querySelector('header')?.textContent).toContain('TaskFlow');
    expect(main).not.toBeNull();
    expect(main?.getAttribute('tabindex')).toBe('-1');
    expect(skipLink?.textContent?.trim()).toBe('Skip to content');
    expect(skipLink?.getAttribute('href')).toBe(`#${main?.id}`);
  });

  it('exposes a labelled brand and Boards navigation only when authenticated', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const brand = element.querySelector<HTMLAnchorElement>('a[aria-label="TaskFlow home"]')!;
    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.querySelector('img')?.getAttribute('alt')).toBe('');
    expect(brand.querySelector('img')?.getAttribute('src')).toBe('taskflow-mark.svg');
    expect(element.querySelector('nav[aria-label="Main navigation"] a')?.textContent).toContain(
      'Boards',
    );
    authenticated.set(false);
    await fixture.whenStable();
    expect(element.querySelector('nav')).toBeNull();
    expect(element.querySelector('button[aria-label="Account menu"]')).toBeNull();
    expect(element.querySelector('a[aria-label="TaskFlow home"]')).not.toBeNull();
  });

  it('keeps auth pages in a brand-only shell and follows their router cross-links', async () => {
    authenticated.set(false);
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const title = TestBed.inject(Title);
    const originalTitle = title.getTitle();
    try {
      await router.navigateByUrl('/login');
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;
      expect(element.querySelectorAll('img[src="taskflow-mark.svg"]')).toHaveLength(1);
      expect(element.querySelector('header a[aria-label="TaskFlow home"]')).not.toBeNull();
      expect(element.querySelector('nav')).toBeNull();
      expect(element.querySelector('button[aria-label="Account menu"]')).toBeNull();
      element.querySelector<HTMLAnchorElement>('main a[href="/register"]')!.click();
      await fixture.whenStable();
      expect(router.url).toBe('/register');
      expect(element.querySelector('main h1')?.textContent).toBe('Create account');
      element.querySelector<HTMLAnchorElement>('main a[href="/login"]')!.click();
      await fixture.whenStable();
      expect(router.url).toBe('/login');
      expect(element.querySelector('main h1')?.textContent).toBe('Log in');
      expect(element.querySelector('nav')).toBeNull();
    } finally {
      title.setTitle(originalTitle);
    }
  });

  it('renders unknown routes inside main and renders Boards at the root', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const router = TestBed.inject(Router);
    const title = TestBed.inject(Title);
    const originalTitle = title.getTitle();

    try {
      await router.navigateByUrl('/unknown-page');
      await fixture.whenStable();
      expect(element.querySelector('main h1')?.textContent).toBe('Page not found');
      element.querySelector('a')?.click();
      await fixture.whenStable();
      expect(document.activeElement).toBe(element.querySelector('main'));
      expect(router.url).toBe('/unknown-page');

      await router.navigateByUrl('/');
      await fixture.whenStable();
      expect(element.querySelector('main h1')?.textContent).toBe('Boards');
      expect(router.url).toBe('/boards');
      expect(element.querySelector('nav a')?.getAttribute('aria-current')).toBe('page');
      expect(element.querySelector('header')?.textContent).toContain('TaskFlow');
    } finally {
      title.setTitle(originalTitle);
    }
  });
});
