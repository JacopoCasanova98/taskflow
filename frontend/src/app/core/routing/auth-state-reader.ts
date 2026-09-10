import { InjectionToken, Signal } from '@angular/core';

/** Readonly navigation state; deliberately excludes credentials and session operations. */
export interface AuthStateReader {
  readonly status: Signal<'initializing' | 'authenticated' | 'anonymous'>;
  readonly isAuthenticated: Signal<boolean>;
}

export const AUTH_STATE = new InjectionToken<AuthStateReader>('AUTH_STATE');
