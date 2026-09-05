import { Routes } from '@angular/router';

export const routes: Routes = [
  // Keep the bootstrap content at / until a real feature owns the landing route.
  { path: '', pathMatch: 'full', children: [], title: 'TaskFlow' },
  {
    path: '**',
    title: 'Page not found | TaskFlow',
    loadComponent: () => import('./core/routing/not-found/not-found').then((m) => m.NotFound),
  },
];
