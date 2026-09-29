import { ApplicationRef, Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { TaskflowDialog } from './taskflow-dialog';

@Component({
  template: `<p>{{ data }}</p>
    <button (click)="ref.close('saved')">Save</button>`,
})
class Content {
  readonly data = inject(DIALOG_DATA);
  readonly ref = inject(DialogRef);
}

describe('TaskFlow dialogs', () => {
  let overlay: HTMLElement;
  let opener: HTMLButtonElement;
  beforeEach(() => {
    TestBed.configureTestingModule({});
    // jsdom has no scrolling implementation; still verify CDK restores the position.
    vi.spyOn(window, 'scroll').mockImplementation(() => {});
    overlay = TestBed.inject(OverlayContainer).getContainerElement();
    opener = document.createElement('button');
    document.body.append(opener);
    opener.focus();
  });
  afterEach(() => {
    opener.remove();
    vi.restoreAllMocks();
  });

  async function open(size: 'sm' | 'md' | 'lg' = 'md', disableClose = false) {
    const ref = TestBed.inject(TaskflowDialog).open<string, string>(Content, {
      title: 'Example action',
      size,
      data: 'Passed to content',
      disableClose,
    });
    await TestBed.inject(ApplicationRef).whenStable();
    return ref;
  }
  function escape() {
    document.body.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }),
    );
  }

  it('labels the dialog, passes data, traps focus and restores it after Escape', async () => {
    vi.spyOn(document.documentElement, 'scrollHeight', 'get').mockReturnValue(2000);
    const ref = await open();
    const dialog = overlay.querySelector('[role="dialog"]')!;
    expect(dialog.getAttribute('aria-label')).toBe('Example action');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.textContent).toContain('Passed to content');
    expect(overlay.querySelectorAll('.cdk-focus-trap-anchor')).toHaveLength(2);
    expect(document.activeElement).toBe(overlay.querySelector('h2'));
    expect(document.documentElement.classList.contains('cdk-global-scrollblock')).toBe(true);
    const closed = vi.fn();
    ref.closed.subscribe(closed);
    escape();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(closed).toHaveBeenCalledExactlyOnceWith(undefined);
    expect(document.activeElement).toBe(opener);
    expect(document.documentElement.classList.contains('cdk-global-scrollblock')).toBe(false);
    expect(window.scroll).toHaveBeenCalledWith(0, 0);
  });

  it.each(['sm', 'md', 'lg'] as const)(
    'uses the responsive %s panel and a labelled close button',
    async (size) => {
      await open(size);
      expect(overlay.querySelector('.tf-dialog-' + size)).not.toBeNull();
      const container = overlay.querySelector('.tf-dialog-panel > .cdk-dialog-container');
      const frame = container?.querySelector('.tf-dialog-frame');
      expect(
        frame?.querySelector(':scope > .tf-dialog-header button[aria-label="Close dialog"]'),
      ).not.toBeNull();
      expect(frame?.querySelector(':scope > .tf-dialog-content')).not.toBeNull();
      const close = overlay.querySelector<HTMLButtonElement>('button[aria-label="Close dialog"]')!;
      close.click();
      expect(overlay.querySelector('[role="dialog"]')).toBeNull();
    },
  );

  it('closes on backdrop click and returns a typed result from content', async () => {
    let ref = await open();
    const closed = vi.fn();
    ref.closed.subscribe(closed);
    overlay.querySelector<HTMLElement>('.tf-dialog-backdrop')!.click();
    expect(closed).toHaveBeenCalledExactlyOnceWith(undefined);
    ref = await open();
    ref.closed.subscribe(closed);
    overlay.querySelector<HTMLButtonElement>('.tf-dialog-content button')!.click();
    expect(closed).toHaveBeenLastCalledWith('saved');
  });

  it('blocks accidental dismissal during an unsafe operation but allows explicit completion', async () => {
    const ref = await open('md', true);
    escape();
    overlay.querySelector<HTMLElement>('.tf-dialog-backdrop')!.click();
    ref.componentInstance!.dismiss();
    expect(overlay.querySelector('[role="dialog"]')).not.toBeNull();
    expect(
      overlay.querySelector<HTMLButtonElement>('button[aria-label="Close dialog"]')!.disabled,
    ).toBe(true);
    ref.close('saved');
    expect(overlay.querySelector('[role="dialog"]')).toBeNull();
  });

  it('supports default size and caller-selected initial focus', async () => {
    const ref = TestBed.inject(TaskflowDialog).open(Content, {
      title: 'Focus',
      autoFocus: 'dialog',
    });
    await TestBed.inject(ApplicationRef).whenStable();
    expect(overlay.querySelector('.tf-dialog-md')).not.toBeNull();
    expect(document.activeElement).toBe(overlay.querySelector('[role="dialog"]'));
    ref.close();
  });
});
