import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

export interface AuthSessionBridge {
  accessToken(): string | null;
  refreshAccessToken(): Observable<string>;
}

export const AUTH_SESSION = new InjectionToken<AuthSessionBridge>('AUTH_SESSION');
