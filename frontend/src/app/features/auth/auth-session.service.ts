import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  defer,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
  timeout,
} from 'rxjs';
import { AuthSessionBridge } from '../../core/http/auth-session-bridge';
import { AuthApi } from './auth-api';
import { AuthenticationResponse, AuthState, LoginRequest, RegisterRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthSessionService implements AuthSessionBridge {
  private readonly api = inject(AuthApi);
  private readonly state = signal<AuthState>({ status: 'initializing' });
  private readonly unavailable = signal(false);
  private revision = 0;
  private refreshing?: Observable<string>;
  private bootstrapRequest?: Observable<void>;

  readonly status = computed(() => this.state().status);
  readonly user = computed(() => {
    const state = this.state();
    return state.status === 'authenticated' ? state.user : null;
  });
  readonly isAuthenticated = computed(() => this.status() === 'authenticated');
  readonly accessToken = computed(() => {
    const state = this.state();
    return state.status === 'authenticated' ? state.accessToken : null;
  });
  readonly accessTokenExpiresAt = computed(() => {
    const state = this.state();
    return state.status === 'authenticated' ? state.accessTokenExpiresAt : null;
  });
  readonly initializationUnavailable = this.unavailable.asReadonly();

  bootstrap(): Observable<void> {
    this.bootstrapRequest ??= this.api.csrf().pipe(
      timeout(10000),
      switchMap(() => this.refreshAccessToken()),
      map(() => undefined),
      catchError((error: unknown) => {
        this.clear();
        this.unavailable.set(!this.sessionInvalid(error));
        return of(undefined);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.bootstrapRequest;
  }

  login(request: LoginRequest) {
    return this.api.login(request).pipe(tap((response) => this.accept(response)));
  }

  register(request: RegisterRequest) {
    return this.api.register(request).pipe(tap((response) => this.accept(response)));
  }

  logout(): Observable<void> {
    // Preserve state on failure: the server has not confirmed refresh-session revocation.
    return this.api.logout().pipe(tap(() => this.clear()));
  }

  refreshAccessToken(): Observable<string> {
    return defer(() => {
      if (!this.refreshing) {
        const revision = this.revision;
        this.refreshing = this.api.refresh().pipe(
          timeout(10000),
          map((response) => {
            // An older refresh must not overwrite a subsequently completed login/logout.
            if (revision !== this.revision) {
              throw new Error('Session changed during refresh.');
            }
            this.accept(response);
            return response.accessToken;
          }),
          catchError((error: unknown) => {
            if (revision === this.revision && this.sessionInvalid(error)) {
              this.clear();
            }
            return throwError(() => error);
          }),
          // Clear once for the shared source, not once for each waiting request.
          finalize(() => {
            this.refreshing = undefined;
          }),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
      }
      return this.refreshing;
    });
  }

  private accept(response: AuthenticationResponse): void {
    this.revision++;
    this.state.set({
      status: 'authenticated',
      user: { ...response.user },
      accessToken: response.accessToken,
      accessTokenExpiresAt: response.accessTokenExpiresAt,
    });
  }

  private clear(): void {
    this.revision++;
    this.state.set({ status: 'anonymous' });
  }

  private sessionInvalid(error: unknown): boolean {
    return (
      error instanceof HttpErrorResponse &&
      error.status === 401 &&
      typeof error.error === 'object' &&
      error.error !== null &&
      error.error.code === 'SESSION_INVALID'
    );
  }
}
