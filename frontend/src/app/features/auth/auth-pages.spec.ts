import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import { Login } from './login/login';
import { Register } from './register/register';

for (const kind of ['login', 'register'] as const) {
  describe(kind + ' Signal Form', () => {
    let fixture: ComponentFixture<Login | Register>;
    let element: HTMLElement;
    let request: ReturnType<typeof vi.fn>;
    let navigate: ReturnType<typeof vi.spyOn>;
    let returnUrl: string | null;
    const password = ' password stays unchanged ';
    beforeEach(async () => {
      request = vi.fn().mockReturnValue(of({}));
      returnUrl = null;
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: AuthSessionService, useValue: { [kind]: request } },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: {
                get queryParamMap() {
                  return convertToParamMap(returnUrl === null ? {} : { returnUrl });
                },
              },
            },
          },
        ],
      });
      navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      fixture =
        kind === 'login' ? TestBed.createComponent(Login) : TestBed.createComponent(Register);
      await fixture.whenStable();
      element = fixture.nativeElement;
    });
    async function fill(email = 'person@example.com', secret = password) {
      for (const [id, value] of [
        ['email', email],
        ['password', secret],
      ]) {
        const input = element.querySelector<HTMLInputElement>('#' + id)!;
        input.value = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
      await fixture.whenStable();
    }
    async function send() {
      element
        .querySelector('form')!
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      await fixture.whenStable();
    }
    it('renders explicit labels, input types, autocomplete and router cross-link', () => {
      expect(element.querySelector('label[for="email"]')?.textContent).toBe('Email');
      expect(element.querySelector('label[for="password"]')?.textContent).toBe('Password');
      expect(element.querySelector('#email')?.getAttribute('type')).toBe('email');
      expect(element.querySelector('#email')?.getAttribute('autocomplete')).toBe('email');
      expect(element.querySelector('#password')?.getAttribute('type')).toBe('password');
      expect(element.querySelector('#password')?.getAttribute('autocomplete')).toBe(
        kind === 'login' ? 'current-password' : 'new-password',
      );
      expect(element.querySelector('a')?.getAttribute('href')).toBe(
        kind === 'login' ? '/register' : '/login',
      );
      expect(element.querySelectorAll('input')).toHaveLength(2);
    });
    it.each([
      ['bad-email', password, 'Enter a valid email address.'],
      ['', password, 'Enter your email address.'],
      ['person@example.com', '', 'Enter your password.'],
      ['a'.repeat(243) + '@example.com', password, 'Use no more than 254 characters.'],
      ['person@example.com', 'a'.repeat(129), 'Use no more than 128 characters.'],
    ])(
      'blocks invalid credentials (%s) with visible validation',
      async (email, secret, message) => {
        await fill(email, secret);
        await send();
        expect(request).not.toHaveBeenCalled();
        expect(element.textContent).toContain(message);
        expect(element.querySelector('input[aria-invalid="true"]')).not.toBeNull();
      },
    );
    it('submits unchanged credentials once and navigates to root', async () => {
      await fill();
      await send();
      expect(request).toHaveBeenCalledExactlyOnceWith({ email: 'person@example.com', password });
      expect(navigate).toHaveBeenCalledWith('/');
    });
    it('prevents concurrent submissions and disables the button until completion', async () => {
      const submission = vi.spyOn(fixture.componentInstance, 'onSubmit');
      const pending = new Subject<unknown>();
      request.mockReturnValue(pending);
      await fill();
      await send();
      await send();
      expect(request).toHaveBeenCalledTimes(1);
      expect(element.querySelector('button')?.disabled).toBe(true);
      pending.next({});
      pending.complete();
      await submission.mock.results[0].value;
      await fixture.whenStable();
      expect(element.querySelector('button')?.disabled).toBe(false);
    });
    it.each([0, 500, 401, 409])(
      'uses a safe retryable fallback for unknown error status %s',
      async (status) => {
        request.mockReturnValue(
          throwError(
            () => new HttpErrorResponse({ status, error: { code: 'UNKNOWN', detail: password } }),
          ),
        );
        await fill();
        await send();
        expect(element.textContent).toContain(
          kind === 'login'
            ? "We couldn't sign you in. Please try again."
            : "We couldn't create your account. Please try again.",
        );
        expect(element.textContent).not.toContain(password);
        expect(element.querySelector('p[aria-live="polite"]')?.textContent).not.toBe('');
        expect(navigate).not.toHaveBeenCalled();
        await send();
        expect(request).toHaveBeenCalledTimes(2);
      },
    );
    if (kind === 'login') {
      it('accepts a short login password and keeps credential failures at form level', async () => {
        request.mockReturnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 401,
                error: { code: 'INVALID_CREDENTIALS', detail: password },
              }),
          ),
        );
        await fill('person@example.com', 'short');
        await send();
        expect(request).toHaveBeenCalledTimes(1);
        expect(element.textContent).toContain('Invalid email or password.');
        expect(element.querySelector('#email-errors')?.textContent?.trim()).toBe('');
        expect(element.querySelector('#password-errors')?.textContent?.trim()).toBe('');
        expect(element.textContent).not.toContain(password);
      });
      it.each(['/boards/123?filter=open', '/', 'https://evil.example', '//evil.example'])(
        'validates returnUrl %s after login',
        async (url) => {
          returnUrl = url;
          await fill();
          await send();
          expect(navigate).toHaveBeenCalledWith(url.startsWith('/boards') ? url : '/');
        },
      );
    } else {
      it('rejects 14-character passwords', async () => {
        await fill('person@example.com', 'a'.repeat(14));
        await send();
        expect(request).not.toHaveBeenCalled();
        expect(element.textContent).toContain('Use at least 15 characters.');
      });
      it.each([15, 128])(
        'accepts %s-character passwords without composition requirements',
        async (length) => {
          await fill('person@example.com', 'a'.repeat(length));
          await send();
          expect(request).toHaveBeenCalledTimes(1);
        },
      );
      it('associates duplicate email with its field and clears it when edited', async () => {
        request.mockReturnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 409,
                error: { code: 'EMAIL_ALREADY_REGISTERED', detail: password },
              }),
          ),
        );
        await fill();
        await send();
        expect(element.querySelector('#email-errors')?.textContent).toContain(
          'An account with this email already exists.',
        );
        expect(element.textContent).not.toContain(password);
        expect(navigate).not.toHaveBeenCalled();
        request.mockReturnValue(of({}));
        await fill('other@example.com');
        await send();
        expect(request).toHaveBeenCalledTimes(2);
        expect(navigate).toHaveBeenCalledWith('/');
      });
    }
  });
}
