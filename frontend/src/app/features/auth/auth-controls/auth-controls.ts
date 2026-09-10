import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthSessionService } from '../auth-session.service';

@Component({
  selector: 'app-auth-controls',
  templateUrl: './auth-controls.html',
  styleUrl: './auth-controls.scss',
})
export class AuthControls {
  readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  readonly submitting = signal(false);
  readonly error = signal('');

  async logout(): Promise<void> {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.session.logout());
    } catch {
      this.error.set("We couldn't log you out. Please try again.");
      this.submitting.set(false);
      return;
    }
    try {
      await this.router.navigateByUrl('/login');
    } finally {
      this.submitting.set(false);
    }
  }
}
