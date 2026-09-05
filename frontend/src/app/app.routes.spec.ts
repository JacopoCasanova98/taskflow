import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Application routing', () => {
  it('shows the fallback for an unknown URL and leaves the root outlet empty on return', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const title = TestBed.inject(Title);
    const originalTitle = title.getTitle();

    try {
      const harness = await RouterTestingHarness.create('/unknown/nested-page');

      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Page not found');
      expect(title.getTitle()).toBe('Page not found | TaskFlow');

      await harness.navigateByUrl('/');

      expect(harness.routeNativeElement).toBeNull();
      expect(title.getTitle()).toBe('TaskFlow');
    } finally {
      title.setTitle(originalTitle);
    }
  });
});
