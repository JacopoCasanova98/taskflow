import { Routes } from '@angular/router';
import { authenticatedGuard } from './core/routing/auth.guards';
import { authRoutes } from './features/auth/auth.routes';

export const routes: Routes = [
  // Keep the shell's content area empty at / until a real feature owns the landing route.
  {
    path: '',
    pathMatch: 'full',
    children: [],
    canActivate: [authenticatedGuard],
    title: 'TaskFlow',
  },
  ...authRoutes,
  {
    path: '**',
    title: 'Page not found | TaskFlow',
    loadComponent: () => import('./core/routing/not-found/not-found').then((m) => m.NotFound),
  },
];
