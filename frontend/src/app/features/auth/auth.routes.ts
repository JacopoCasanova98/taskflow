import { Routes } from '@angular/router';
import { anonymousOnlyGuard } from '../../core/routing/auth.guards';

export const authRoutes: Routes = [
  {
    path: 'login',
    title: 'Log in | TaskFlow',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account | TaskFlow',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () => import('./register/register').then((m) => m.Register),
  },
];
