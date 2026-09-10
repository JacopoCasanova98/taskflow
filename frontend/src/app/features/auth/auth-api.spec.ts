import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { AuthApi } from './auth-api';

describe('AuthApi', () => {
  let api: AuthApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(AuthApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  });

  it('uses the six backend operations and preserves their response bodies', () => {
    const credentials = { email: 'user@example.com', password: 'test-only' };
    const user = { id: 'user-id', email: credentials.email };
    const response = {
      user,
      accessToken: 'test-token',
      accessTokenExpiresAt: '2026-09-09T12:15:00Z',
    };
    for (const operation of ['login', 'register'] as const) {
      api[operation](credentials).subscribe((value) => expect(value).toEqual(response));
      const request = http.expectOne(`/api/auth/${operation}`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(credentials);
      request.flush(response);
    }
    api.refresh().subscribe((value) => expect(value).toEqual(response));
    const refresh = http.expectOne('/api/auth/refresh');
    expect(refresh.request.method).toBe('POST');
    refresh.flush(response);
    api.me().subscribe((value) => expect(value).toEqual(user));
    const me = http.expectOne('/api/auth/me');
    expect(me.request.method).toBe('GET');
    me.flush(user);
    for (const operation of ['csrf', 'logout'] as const) {
      let completed = false;
      api[operation]().subscribe({
        complete: () => {
          completed = true;
        },
      });
      const request = http.expectOne(`/api/auth/${operation}`);
      expect(request.request.method).toBe(operation === 'csrf' ? 'GET' : 'POST');
      request.flush(null, { status: 204, statusText: 'No Content' });
      expect(completed).toBe(true);
    }
  });

  it('uses Angular actual XSRF cookie/header handling for POST but not GET', () => {
    document.cookie = 'XSRF-TOKEN=test-xsrf; Path=/';
    api.logout().subscribe();
    const post = http.expectOne('/api/auth/logout');
    expect(post.request.headers.get('X-XSRF-TOKEN')).toBe('test-xsrf');
    expect(post.request.withCredentials).toBe(false);
    post.flush(null);
    api.csrf().subscribe();
    const get = http.expectOne('/api/auth/csrf');
    expect(get.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    get.flush(null);
  });
});
