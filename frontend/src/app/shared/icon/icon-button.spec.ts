import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { IconButton } from './icon-button';
import { Icon } from './icon';

@Component({
  imports: [IconButton, Icon],
  template: `<button
    appIconButton="Remove item"
    [danger]="true"
    [disabled]="disabled()"
    (click)="clicks = clicks + 1"
  >
    <app-icon name="close" />
  </button>`,
})
class Host {
  readonly disabled = signal(false);
  clicks = 0;
}

describe('Icon buttons', () => {
  it('names a native button, hides decorative SVG and retains native disabled semantics', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.type).toBe('button');
    expect(button.getAttribute('aria-label')).toBe('Remove item');
    expect(button.getAttribute('title')).toBe('Remove item');
    expect(button.classList.contains('tf-icon-button-danger')).toBe(true);
    expect(button.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    button.click();
    expect(fixture.componentInstance.clicks).toBe(1);
    fixture.componentInstance.disabled.set(true);
    await fixture.whenStable();
    button.click();
    expect(fixture.componentInstance.clicks).toBe(1);
  });
});
