import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthControls } from './features/auth/auth-controls/auth-controls';

@Component({
  imports: [RouterOutlet, AuthControls],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
