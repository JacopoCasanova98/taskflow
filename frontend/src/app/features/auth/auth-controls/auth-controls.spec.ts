import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthSessionService } from '../auth-session.service';
import { AuthControls } from './auth-controls';

describe('Authenticated shell controls', () => {
  let fixture: ComponentFixture<AuthControls>;
  const authenticated = signal(true);
  let logout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  beforeEach(async () => {
    authenticated.set(true);
    logout = vi.fn().mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthSessionService,
          useValue: {
            isAuthenticated: authenticated.asReadonly(),
            user: signal({ email: 'person@example.com' }),
            logout: logout,
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(AuthControls);
    await fixture.whenStable();
  });
  it('shows email and logout only while authenticated', async () => {
    expect(fixture.nativeElement.textContent).toContain('person@example.com');
    expect(fixture.nativeElement.querySelector('button').textContent).toContain('Log out');
    authenticated.set(false);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
  it('navigates to login only after logout succeeds, preventing duplicates', async () => {
    const pending = new Subject<void>();
    logout.mockReturnValue(pending);
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    await fixture.componentInstance.logout();
    expect(logout).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    pending.next();
    pending.complete();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledExactlyOnceWith('/login');
  });
  it('preserves authenticated UI on failure, announces a safe error and permits retry', async () => {
    logout.mockReturnValue(throwError(() => new Error('private infrastructure detail')));
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    expect(navigate).not.toHaveBeenCalled();
    expect(authenticated()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('person@example.com');
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent).toBe(
      "We couldn't log you out. Please try again.",
    );
    expect(fixture.nativeElement.textContent).not.toContain('private infrastructure detail');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });
});
