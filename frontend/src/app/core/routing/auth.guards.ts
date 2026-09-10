import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AUTH_STATE } from './auth-state-reader';

// Navigation UX only. Backend authentication and authorization remain authoritative.
export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AUTH_STATE);
  // Bootstrap normally completes before navigation. Cancel unexpected unresolved navigation.
  if (auth.status() === 'initializing') return false;
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const anonymousOnlyGuard: CanActivateFn = () => {
  const auth = inject(AUTH_STATE);
  if (auth.status() === 'initializing') return false;
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;
};
