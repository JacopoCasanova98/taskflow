import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthSessionService } from './features/auth/auth-session.service';
import { AuthControls } from './features/auth/auth-controls/auth-controls';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AuthControls],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  readonly session = inject(AuthSessionService);
}
