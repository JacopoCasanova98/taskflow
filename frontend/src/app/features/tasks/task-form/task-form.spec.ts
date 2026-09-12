import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskForm } from './task-form';
import { TaskMutationResult } from '../task-management';
import { Task } from '../task.models';

describe('Task Signal Form', () => {
  let fixture: ComponentFixture<TaskForm>;
  let element: HTMLElement;
  let save: ReturnType<typeof vi.fn>;
  let closed: ReturnType<typeof vi.fn<() => void>>;
  async function mount(task: Task | null = null) {
    fixture = TestBed.createComponent(TaskForm);
    fixture.componentRef.setInput('inputId', 'task-form');
    fixture.componentRef.setInput('action', 'Create task');
    fixture.componentRef.setInput('save', save);
    fixture.componentRef.setInput('initialTask', task);
    fixture.componentInstance.closed.subscribe(() => closed());
    await fixture.whenStable();
    element = fixture.nativeElement;
  }
  beforeEach(async () => {
    save = vi.fn().mockResolvedValue({ success: true });
    closed = vi.fn();
    await mount();
  });
  async function fill(field: string, value: string) {
    const input = element.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
      '#task-form-' + field,
    )!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }
  async function submit() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await Promise.resolve();
    await fixture.whenStable();
  }
  it('labels all fields and uses native textarea, select, and date input', () => {
    expect(Array.from(element.querySelectorAll('label'), (label) => label.htmlFor)).toEqual([
      'task-form-title',
      'task-form-description',
      'task-form-priority',
      'task-form-dueDate',
    ]);
    expect(element.querySelector('textarea')).not.toBeNull();
    expect(element.querySelector('select')?.value).toBe('MEDIUM');
    expect(element.querySelector<HTMLInputElement>('#task-form-dueDate')?.type).toBe('date');
  });
  it.each(['', '   ', 'x'.repeat(201)])('rejects invalid title %j', async (value) => {
    await fill('title', value);
    await submit();
    expect(save).not.toHaveBeenCalled();
    expect(element.querySelector('#task-form-title-errors')?.textContent?.trim()).not.toBe('');
  });
  it('accepts a 200-character title', async () => {
    await fill('title', 'x'.repeat(200));
    await submit();
    expect(save).toHaveBeenCalledExactlyOnceWith({
      title: 'x'.repeat(200),
      description: null,
      priority: 'MEDIUM',
      dueDate: null,
    });
  });
  it('trims title edges and preserves case and interior spaces', async () => {
    await fill('title', '  Fix   login bug  ');
    await submit();
    expect(save).toHaveBeenCalledExactlyOnceWith({
      title: 'Fix   login bug',
      description: null,
      priority: 'MEDIUM',
      dueDate: null,
    });
  });
  it.each(['', '  \n  '])('normalizes optional description %j to null', async (description) => {
    await fill('title', 'Task');
    await fill('description', description);
    await submit();
    expect(save.mock.calls[0][0].description).toBeNull();
  });
  it('accepts 4000 description characters', async () => {
    await fill('title', 'Task');
    await fill('description', 'x'.repeat(4000));
    await submit();
    expect(save.mock.calls[0][0].description).toHaveLength(4000);
  });
  it('rejects overlong description', async () => {
    await fill('title', 'Task');
    await fill('description', 'x'.repeat(4001));
    await submit();
    expect(save).not.toHaveBeenCalled();
    expect(element.querySelector('#task-form-description-errors')?.textContent).toContain('4000');
  });
  it('preserves internal whitespace and line breaks in description', async () => {
    await fill('title', 'Task');
    await fill('description', '  Line   one\n\nLine two  ');
    await submit();
    expect(save.mock.calls[0][0].description).toBe('Line   one\n\nLine two');
  });
  it.each(['LOW', 'MEDIUM', 'HIGH'])('submits explicit priority %s', async (priority) => {
    await fill('title', 'Task');
    await fill('priority', priority);
    await submit();
    expect(save.mock.calls[0][0].priority).toBe(priority);
  });
  it('rejects an invalid priority', async () => {
    await fill('title', 'Task');
    await fill('priority', 'INVALID');
    await submit();
    expect(save).not.toHaveBeenCalled();
  });
  it('preserves calendar dates as strings and clears an empty date to null', async () => {
    await fill('title', 'Task');
    await fill('dueDate', '2026-09-30');
    await submit();
    expect(save.mock.calls[0][0].dueDate).toBe('2026-09-30');
    await fill('dueDate', '');
    await submit();
    expect(save.mock.calls[1][0].dueDate).toBeNull();
  });
  it('initializes edit from all canonical fields and permits clearing optional content', async () => {
    fixture.destroy();
    await mount({
      id: 't',
      columnId: 'c',
      position: 0,
      title: 'Existing',
      description: 'Description',
      priority: 'HIGH',
      dueDate: '2026-09-30',
      createdAt: '',
      updatedAt: '',
    });
    expect(element.querySelector<HTMLInputElement>('#task-form-title')?.value).toBe('Existing');
    expect(element.querySelector('textarea')?.value).toBe('Description');
    expect(element.querySelector('select')?.value).toBe('HIGH');
    expect(element.querySelector<HTMLInputElement>('#task-form-dueDate')?.value).toBe('2026-09-30');
    await fill('description', '');
    await fill('dueDate', '');
    await submit();
    expect(save).toHaveBeenCalledExactlyOnceWith({
      title: 'Existing',
      description: null,
      priority: 'HIGH',
      dueDate: null,
    });
  });
  it('prevents concurrent submission and disables Cancel while pending', async () => {
    let finish!: (result: TaskMutationResult) => void;
    save.mockReturnValue(new Promise<TaskMutationResult>((resolve) => (finish = resolve)));
    await fill('title', 'Task');
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
  it('cancels without submitting', () => {
    element.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    expect(closed).toHaveBeenCalledOnce();
    expect(save).not.toHaveBeenCalled();
  });
  it('retains input and announces failed submission', async () => {
    save.mockResolvedValue({
      success: false,
      error: "We couldn't create the task. Please try again.",
    });
    await fill('title', '  Task  ');
    await fill('description', '  Notes  ');
    await submit();
    expect(element.querySelector<HTMLInputElement>('#task-form-title')?.value).toBe('  Task  ');
    expect(element.querySelector('textarea')?.value).toBe('  Notes  ');
    expect(closed).not.toHaveBeenCalled();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain("We couldn't create");
  });
  it('blocks submission while a Column write is pending', async () => {
    fixture.componentRef.setInput('disabled', true);
    await fill('title', 'Task');
    await submit();
    expect(save).not.toHaveBeenCalled();
  });
});
