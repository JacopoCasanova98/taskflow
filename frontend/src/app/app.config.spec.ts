import { AUTH_STATE } from './core/routing/auth-state-reader';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/config/api-base-url';
import { AUTH_SESSION } from './core/http/auth-session-bridge';
import { AuthSessionService } from './features/auth/auth-session.service';

describe('Application configuration', () => {
  it('initializes once through csrf then refresh with the configured Angular XSRF names', async () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const initialization = TestBed.inject(ApplicationInitStatus);
    try {
      // TestBed runs application initializers when the test injector is first created.
      expect(TestBed.inject(AUTH_STATE)).toBe(TestBed.inject(AuthSessionService));
      expect(TestBed.inject(API_BASE_URL)).toBe('/api');
      expect(TestBed.inject(AUTH_SESSION)).toBe(TestBed.inject(AuthSessionService));
      http.expectNone('/api/auth/refresh');
      const csrf = http.expectOne('/api/auth/csrf');
      expect(csrf.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      document.cookie = 'XSRF-TOKEN=bootstrap-xsrf; Path=/';
      csrf.flush(null);
      const refresh = http.expectOne('/api/auth/refresh');
      expect(refresh.request.headers.get('X-XSRF-TOKEN')).toBe('bootstrap-xsrf');
      expect(refresh.request.headers.has('Authorization')).toBe(false);
      refresh.flush({ code: 'SESSION_INVALID' }, { status: 401, statusText: 'Unauthorized' });
      await initialization.donePromise;
      expect(TestBed.inject(AuthSessionService).status()).toBe('anonymous');
      TestBed.inject(AuthSessionService).bootstrap().subscribe();
      http.verify();
    } finally {
      document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    }
  });
});
