import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ColumnNameForm } from './column-name-form';
import { ColumnMutationResult } from '../column-management';

describe('Column name Signal Form', () => {
  let fixture: ComponentFixture<ColumnNameForm>;
  let element: HTMLElement;
  let save: ReturnType<typeof vi.fn>;
  let closed: ReturnType<typeof vi.fn<() => void>>;
  beforeEach(async () => {
    fixture = TestBed.createComponent(ColumnNameForm);
    save = vi.fn().mockResolvedValue({ success: true });
    closed = vi.fn();
    fixture.componentRef.setInput('inputId', 'column-name');
    fixture.componentRef.setInput('action', 'Save name');
    fixture.componentRef.setInput('save', save);
    fixture.componentInstance.closed.subscribe(() => closed());
    await fixture.whenStable();
    element = fixture.nativeElement;
  });
  async function fill(value: string) {
    const input = element.querySelector('input')!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }
  async function submit() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
  }
  it('provides a label associated with its input', () => {
    expect(element.querySelector('label')?.textContent).toBe('Column name');
    expect(element.querySelector('label')?.htmlFor).toBe(element.querySelector('input')?.id);
  });
  it.each(['', '   ', 'x'.repeat(121)])('rejects invalid name %j', async (name) => {
    await fill(name);
    await submit();
    expect(save).not.toHaveBeenCalled();
    expect(fixture.componentInstance.nameForm.name().invalid()).toBe(true);
    expect(element.querySelector('[aria-live]')?.textContent?.trim()).not.toBe('');
  });
  it('accepts 120 characters', async () => {
    await fill('x'.repeat(120));
    await submit();
    expect(save).toHaveBeenCalledExactlyOnceWith('x'.repeat(120));
    expect(closed).toHaveBeenCalledOnce();
  });
  it('trims only edges, preserving case and interior whitespace', async () => {
    await fill('  In   Progress  ');
    await submit();
    expect(save).toHaveBeenCalledExactlyOnceWith('In   Progress');
  });
  it('prevents duplicate pending submissions and disables cancel', async () => {
    let finish!: (value: ColumnMutationResult) => void;
    save.mockReturnValue(new Promise<ColumnMutationResult>((resolve) => (finish = resolve)));
    await fill('Doing');
    await submit();
    await submit();
    expect(save).toHaveBeenCalledOnce();
    expect(Array.from(element.querySelectorAll('button')).every((button) => button.disabled)).toBe(
      true,
    );
    finish({ success: true });
    await fixture.whenStable();
    expect(closed).toHaveBeenCalledOnce();
  });
  it('retains entered input and announces safe failure', async () => {
    save.mockResolvedValue({
      success: false,
      error: "We couldn't rename the column. Please try again.",
    });
    await fill('  In   Progress  ');
    await submit();
    expect(element.querySelector('input')?.value).toBe('  In   Progress  ');
    expect(element.querySelector('[aria-live]')?.textContent).toContain(
      "We couldn't rename the column.",
    );
    expect(closed).not.toHaveBeenCalled();
  });
  it('cancels without saving', () => {
    element.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    expect(closed).toHaveBeenCalledOnce();
    expect(save).not.toHaveBeenCalled();
  });
  it('does not submit while another Column write is pending', async () => {
    fixture.componentRef.setInput('disabled', true);
    await fill('Doing');
    await submit();
    expect(save).not.toHaveBeenCalled();
  });
});
