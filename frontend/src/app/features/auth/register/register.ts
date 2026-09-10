import { Component, inject, signal } from '@angular/core';
import {
  email,
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthSessionService } from '../auth-session.service';
import { isAuthProblem } from '../auth-problem';

@Component({
  selector: 'app-register',
  imports: [FormField, RouterLink],
  templateUrl: './register.html',
  styleUrl: '../auth-page.scss',
})
export class Register {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  private readonly model = signal({ email: '', password: '' });
  readonly serverError = signal('');
  readonly authForm = form(this.model, (path) => {
    required(path.email, { message: 'Enter your email address.' });
    email(path.email, { message: 'Enter a valid email address.' });
    maxLength(path.email, 254, { message: 'Use no more than 254 characters.' });
    required(path.password, { message: 'Enter your password.' });
    minLength(path.password, 15, { message: 'Use at least 15 characters.' });
    maxLength(path.password, 128, { message: 'Use no more than 128 characters.' });
  });

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.authForm().submitting()) return;
    this.serverError.set('');
    await submit(this.authForm, async (field) => {
      try {
        await firstValueFrom(this.session.register(field().value()));
      } catch (error: unknown) {
        if (isAuthProblem(error, 409, 'EMAIL_ALREADY_REGISTERED')) {
          return {
            kind: 'emailTaken',
            message: 'An account with this email already exists.',
            fieldTree: field.email,
          };
        }
        this.serverError.set("We couldn't create your account. Please try again.");
        return;
      }
      await this.router.navigateByUrl('/');
      return;
    });
  }
}
