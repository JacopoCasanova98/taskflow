import {
  HttpClient,
  HttpContext,
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, of, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api-base-url';
import { AUTH_SESSION } from './auth-session-bridge';

const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const base = inject(API_BASE_URL);
  const path = request.url.split(/[?#]/, 1)[0];
  // Only the configured relative namespace is supported; never inherit credentials for absolute URLs.
  const eligible =
    base.startsWith('/') &&
    !base.startsWith('//') &&
    (path === base || path.startsWith(`${base}/`)) &&
    !(request.method === 'GET' && path === `${base}/auth/csrf`) &&
    !(
      request.method === 'POST' &&
      ['register', 'login', 'refresh', 'logout'].some((action) => path === `${base}/auth/${action}`)
    );
  if (!eligible) {
    return next(request);
  }

  const session = inject(AUTH_SESSION);
  const http = inject(HttpClient);
  const token = session.accessToken();
  if (!token) {
    return next(request);
  }
  const withBearer = (value: string) =>
    request.clone({ setHeaders: { Authorization: `Bearer ${value}` } });
  return next(withBearer(token)).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        request.context.get(AUTH_RETRIED) ||
        !session.accessToken()
      ) {
        return throwError(() => error);
      }
      // A late 401 may belong to the previous token after another request already refreshed it.
      const current = session.accessToken();
      const refresh =
        current !== token && current !== null ? of(current) : session.refreshAccessToken();
      return refresh.pipe(
        switchMap(() => {
          const context = new HttpContext();
          for (const key of request.context.keys()) {
            context.set(key, request.context.get(key));
          }
          context.set(AUTH_RETRIED, true);
          // Re-enter HttpClient so Angular's built-in XSRF stage sees the cookie renewed by refresh.
          // Remove only the previous header; Angular alone reads the cookie and adds the fresh value.
          // The copied request-local marker prevents another recovery on the retry.
          return http.request(
            request.clone({ context, headers: request.headers.delete('X-XSRF-TOKEN') }),
          );
        }),
      );
    }),
  );
};
