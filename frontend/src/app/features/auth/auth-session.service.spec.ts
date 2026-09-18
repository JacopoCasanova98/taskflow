import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { AUTH_SESSION } from '../../core/http/auth-session-bridge';
import { authInterceptor } from '../../core/http/auth.interceptor';
import { AuthSessionService } from './auth-session.service';

const response = (accessToken = 'test-access') => ({
  user: { id: 'user-id', email: 'user@example.com' },
  accessToken,
  accessTokenExpiresAt: '2026-09-09T12:15:00Z',
});
const credentials = { email: 'user@example.com', password: 'test-only' };
const invalid = { code: 'SESSION_INVALID' };
const unauthorized = { status: 401, statusText: 'Unauthorized' };
const unavailable = { status: 503, statusText: 'Unavailable' };

describe('Auth session and HTTP recovery', () => {
  let session: AuthSessionService;
  let http: HttpTestingController;
  let client: HttpClient;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        { provide: AUTH_SESSION, useExisting: AuthSessionService },
      ],
    });
    session = TestBed.inject(AuthSessionService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });
  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  });

  function login() {
    session.login(credentials).subscribe();
    http.expectOne('/api/auth/login').flush(response());
  }

  it('starts initializing, bootstraps csrf before refresh once, and uses returned user data', () => {
    expect(session.status()).toBe('initializing');
    expect(session.accessToken()).toBeNull();
    session.bootstrap().subscribe();
    session.bootstrap().subscribe();
    http.expectNone('/api/auth/refresh');
    http.expectOne('/api/auth/csrf').flush(null);
    expect(session.status()).toBe('initializing');
    http.expectOne('/api/auth/refresh').flush(response());
    expect(session.isAuthenticated()).toBe(true);
    expect(session.user()).toEqual(response().user);
    expect(session.accessTokenExpiresAt()).toBe(response().accessTokenExpiresAt);
    session.bootstrap().subscribe();
    http.expectNone('/api/auth/me');
    http.expectNone('/api/auth/refresh');
    http.expectNone('/api/auth/csrf');
  });

  it('finishes bootstrap anonymously for SESSION_INVALID without an infrastructure flag', () => {
    let completed = false;
    session.bootstrap().subscribe({
      complete: () => {
        completed = true;
      },
    });
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/refresh').flush(invalid, unauthorized);
    expect(completed).toBe(true);
    expect(session.status()).toBe('anonymous');
    expect(session.initializationUnavailable()).toBe(false);
  });

  it('finishes bootstrap with a safe flag on infrastructure failure', () => {
    session.bootstrap().subscribe();
    http.expectOne('/api/auth/csrf').flush(null, unavailable);
    expect(session.status()).toBe('anonymous');
    expect(session.initializationUnavailable()).toBe(true);
    http.expectNone('/api/auth/refresh');
  });

  it('finishes bootstrap anonymously when refresh infrastructure is unavailable', () => {
    session.bootstrap().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/refresh').flush({}, unavailable);
    expect(session.status()).toBe('anonymous');
    expect(session.initializationUnavailable()).toBe(true);
  });

  it('bounds a stalled bootstrap without timing sleeps or repeated requests', async () => {
    vi.useFakeTimers();
    try {
      let completed = false;
      session.bootstrap().subscribe({
        complete: () => {
          completed = true;
        },
      });
      const csrf = http.expectOne('/api/auth/csrf');
      await vi.advanceTimersByTimeAsync(10000);
      expect(completed).toBe(true);
      expect(csrf.cancelled).toBe(true);
      expect(session.status()).toBe('anonymous');
      expect(session.initializationUnavailable()).toBe(true);
      http.expectNone('/api/auth/refresh');
    } finally {
      vi.useRealTimers();
    }
  });

  it('login and register replace state; failed login and logout preserve it and propagate errors', () => {
    login();
    session.register(credentials).subscribe();
    http.expectOne('/api/auth/register').flush(response('replacement'));
    expect(session.accessToken() === 'replacement').toBe(true);
    for (const action of ['login', 'logout'] as const) {
      const error = vi.fn();
      const operation: Observable<unknown> =
        action === 'login' ? session.login(credentials) : session.logout();
      operation.subscribe({ error });
      http.expectOne(`/api/auth/${action}`).flush({}, unavailable);
      expect(error).toHaveBeenCalledOnce();
      expect(session.isAuthenticated()).toBe(true);
      expect(session.accessToken() === 'replacement').toBe(true);
    }
    session.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    expect(session.status()).toBe('anonymous');
    expect(session.user()).toBeNull();
    expect(session.accessToken()).toBeNull();
    expect(session.accessTokenExpiresAt()).toBeNull();
  });

  it('keeps authenticated credentials in memory only', () => {
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    login();
    expect(session.user()).toEqual(response().user);
    expect(session.accessToken()).toBe('test-access');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('attaches Bearer only to protected relative API requests, including me', () => {
    login();
    for (const url of ['/api', '/api/tasks', '/api/auth/me?details=true']) {
      client.get(url).subscribe();
      const request = http.expectOne(url);
      expect(
        request.request.headers.get('Authorization') === `Bearer ${response().accessToken}`,
      ).toBe(true);
      request.flush({});
    }
    for (const url of [
      'https://example.com/api/tasks',
      '//example.com/api/tasks',
      '/assets/file',
      '/api-other',
      '/other/api/tasks',
    ]) {
      client.get(url).subscribe({ error: () => undefined });
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({}, unauthorized);
    }
    for (const action of ['csrf', 'register', 'login', 'refresh', 'logout']) {
      client
        .request(action === 'csrf' ? 'GET' : 'POST', `/api/auth/${action}`)
        .subscribe({ error: () => undefined });
      const request = http.expectOne(`/api/auth/${action}`);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({}, unauthorized);
    }
    http.expectNone('/api/auth/refresh');
  });

  it('does not refresh anonymous requests or non-401 failures', () => {
    client.get('/api/auth/me').subscribe({ error: () => undefined });
    const anonymous = http.expectOne('/api/auth/me');
    expect(anonymous.request.headers.has('Authorization')).toBe(false);
    anonymous.flush({}, unauthorized);
    login();
    client.get('/api/tasks').subscribe({ error: () => undefined });
    http.expectOne('/api/tasks').flush({}, unavailable);
    http.expectNone('/api/auth/refresh');
  });

  it('shares one refresh for concurrent 401s and retries each original with the new Bearer once', () => {
    login();
    const errors = vi.fn();
    for (const id of [1, 2, 3]) {
      client.get(`/api/tasks/${id}`).subscribe({ error: errors });
      http.expectOne(`/api/tasks/${id}`).flush({}, unauthorized);
    }
    const refresh = http.expectOne('/api/auth/refresh');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush(response('new-access'));
    for (const id of [1, 2, 3]) {
      const retry = http.expectOne(`/api/tasks/${id}`);
      expect(retry.request.headers.get('Authorization') === 'Bearer new-access').toBe(true);
      if (id === 3) {
        retry.flush({}, unauthorized);
      } else {
        retry.flush({});
      }
    }
    expect(errors).toHaveBeenCalledTimes(1);
    http.expectNone('/api/auth/refresh');
  });

  it('re-enters built-in XSRF handling on unsafe retry after cookie renewal', () => {
    login();
    document.cookie = 'XSRF-TOKEN=before; Path=/';
    client.post('/api/tasks', { title: 'unchanged' }).subscribe();
    const original = http.expectOne('/api/tasks');
    expect(original.request.headers.get('X-XSRF-TOKEN')).toBe('before');
    original.flush({}, unauthorized);
    document.cookie = 'XSRF-TOKEN=after; Path=/';
    http.expectOne('/api/auth/refresh').flush(response('new-access'));
    const retry = http.expectOne('/api/tasks');
    expect(retry.request.headers.get('X-XSRF-TOKEN')).toBe('after');
    expect(retry.request.body).toEqual({ title: 'unchanged' });
    retry.flush({});
  });

  it('uses an already renewed token for a late old-token 401 without rotating twice', () => {
    login();
    client.get('/api/first').subscribe();
    client.get('/api/late').subscribe();
    const first = http.expectOne('/api/first');
    const late = http.expectOne('/api/late');
    first.flush({}, unauthorized);
    http.expectOne('/api/auth/refresh').flush(response('renewed'));
    http.expectOne('/api/first').flush({});
    late.flush({}, unauthorized);
    const retry = http.expectOne('/api/late');
    expect(retry.request.headers.get('Authorization') === 'Bearer renewed').toBe(true);
    retry.flush({});
    http.expectNone('/api/auth/refresh');
  });

  it('clears state and fails all waiters on SESSION_INVALID without retrying', () => {
    login();
    const errors = vi.fn();
    for (const url of ['/api/a', '/api/b']) {
      client.get(url).subscribe({ error: errors });
      http.expectOne(url).flush({}, unauthorized);
    }
    http.expectOne('/api/auth/refresh').flush(invalid, unauthorized);
    expect(errors).toHaveBeenCalledTimes(2);
    expect(session.status()).toBe('anonymous');
    expect(session.accessToken()).toBeNull();
    http.expectNone('/api/a');
    http.expectNone('/api/b');
  });

  it('propagates refresh infrastructure failure and releases the single-flight coordinator', () => {
    login();
    const error = vi.fn();
    session.refreshAccessToken().subscribe({ error });
    http.expectOne('/api/auth/refresh').flush({}, unavailable);
    expect(error).toHaveBeenCalledOnce();
    expect(session.isAuthenticated()).toBe(true);
    session.refreshAccessToken().subscribe();
    http.expectOne('/api/auth/refresh').flush(response('renewed'));
    expect(session.accessToken() === 'renewed').toBe(true);
  });

  it('does not let a pending refresh restore state after successful logout', () => {
    login();
    const error = vi.fn();
    session.refreshAccessToken().subscribe({ error });
    const refresh = http.expectOne('/api/auth/refresh');
    session.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    refresh.flush(response('stale'));
    expect(error).toHaveBeenCalledOnce();
    expect(session.status()).toBe('anonymous');
  });
});
