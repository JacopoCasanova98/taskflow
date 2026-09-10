import { signal } from '@angular/core';
import { AUTH_STATE } from './core/routing/auth-state-reader';
import { AuthSessionService } from './features/auth/auth-session.service';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: AUTH_STATE,
          useValue: { status: signal('authenticated'), isAuthenticated: signal(true) },
        },
        {
          provide: AuthSessionService,
          useValue: {
            isAuthenticated: signal(true),
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

  it('renders unknown routes inside main and clears routed content at the root', async () => {
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
      expect(element.querySelector('main')?.textContent?.trim()).toBe('');
      expect(element.querySelector('header')?.textContent).toContain('TaskFlow');
    } finally {
      title.setTitle(originalTitle);
    }
  });
});
