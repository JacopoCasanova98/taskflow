import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { API_BASE_URL } from './core/config/api-base-url';
import { AUTH_SESSION } from './core/http/auth-session-bridge';
import { authInterceptor } from './core/http/auth.interceptor';
import { AuthSessionService } from './features/auth/auth-session.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authInterceptor]),
    ),
    { provide: AUTH_SESSION, useExisting: AuthSessionService },
    provideAppInitializer(() => inject(AuthSessionService).bootstrap()),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    provideRouter(routes),
  ],
};
