import { Routes } from '@angular/router';
import { boardRoutes } from './features/boards/boards.routes';
import { authRoutes } from './features/auth/auth.routes';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'boards' },
  ...boardRoutes,
  ...authRoutes,
  {
    path: '**',
    title: 'Page not found | TaskFlow',
    loadComponent: () => import('./core/routing/not-found/not-found').then((m) => m.NotFound),
  },
];
