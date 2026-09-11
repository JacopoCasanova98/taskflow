import { Routes } from '@angular/router';
import { authenticatedGuard } from '../../core/routing/auth.guards';

export const boardRoutes: Routes = [
  {
    path: 'boards',
    title: 'Boards | TaskFlow',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./board-list/board-list').then((m) => m.BoardList),
  },
];
