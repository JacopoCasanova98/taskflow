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
  async function openMenu() {
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    return document.querySelector<HTMLElement>('[role="menu"]')!;
  }
  async function clickLogout() {
    const menu = await openMenu();
    menu.querySelector<HTMLButtonElement>('[role="menuitem"]')!.click();
  }
  it('shows email and logout only while authenticated', async () => {
    expect(fixture.nativeElement.textContent).not.toContain('person@example.com');
    const menu = await openMenu();
    expect(document.querySelector('[role="menu"]')?.textContent).toContain('person@example.com');
    expect(menu.querySelector('[role="menuitem"]')?.textContent).toContain('Log out');
    authenticated.set(false);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    expect(document.querySelector('[role="menu"]')).toBeNull();
  });
  it('navigates to login only after logout succeeds, preventing duplicates', async () => {
    const pending = new Subject<void>();
    logout.mockReturnValue(pending);
    await clickLogout();
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
    await clickLogout();
    await fixture.whenStable();
    expect(navigate).not.toHaveBeenCalled();
    expect(authenticated()).toBe(true);
    await openMenu();
    expect(document.querySelector('[role="menu"]')?.textContent).toContain('person@example.com');
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent.trim()).toBe(
      "We couldn't log you out. Please try again.",
    );
    expect(fixture.nativeElement.textContent).not.toContain('private infrastructure detail');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    logout.mockReturnValue(of(undefined));
    document.querySelector<HTMLButtonElement>('[role="menuitem"]')!.click();
    await fixture.whenStable();
    expect(logout).toHaveBeenCalledTimes(2);
    expect(navigate).toHaveBeenCalledExactlyOnceWith('/login');
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent.trim()).toBe('');
  });
  it('supports keyboard opening, Escape focus restoration and outside dismissal', async () => {
    const trigger = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    trigger.focus();
    trigger.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: 40, bubbles: true }),
    );
    await fixture.whenStable();
    const item = document.querySelector<HTMLElement>('[role="menuitem"]')!;
    expect(item).not.toBeNull();
    expect(document.activeElement).toBe(item);
    item.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    await fixture.whenStable();
    expect(document.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    await openMenu();
    document.body.click();
    await fixture.whenStable();
    expect(document.querySelector('[role="menu"]')).toBeNull();
  });
});
