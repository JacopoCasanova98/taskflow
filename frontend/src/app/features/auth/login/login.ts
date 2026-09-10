import { Component, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthSessionService } from '../auth-session.service';
import { isAuthProblem } from '../auth-problem';
import { safeReturnUrl } from '../safe-return-url';

@Component({
  selector: 'app-login',
  imports: [FormField, RouterLink],
  templateUrl: './login.html',
  styleUrl: '../auth-page.scss',
})
export class Login {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly model = signal({ email: '', password: '' });
  readonly serverError = signal('');
  readonly authForm = form(this.model, (path) => {
    required(path.email, { message: 'Enter your email address.' });
    email(path.email, { message: 'Enter a valid email address.' });
    maxLength(path.email, 254, { message: 'Use no more than 254 characters.' });
    required(path.password, { message: 'Enter your password.' });

    maxLength(path.password, 128, { message: 'Use no more than 128 characters.' });
  });

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.authForm().submitting()) return;
    this.serverError.set('');
    await submit(this.authForm, async (field) => {
      try {
        await firstValueFrom(this.session.login(field().value()));
      } catch (error: unknown) {
        this.serverError.set(
          isAuthProblem(error, 401, 'INVALID_CREDENTIALS')
            ? 'Invalid email or password.'
            : "We couldn't sign you in. Please try again.",
        );
        return;
      }
      await this.router.navigateByUrl(
        safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')),
      );
    });
  }
}
