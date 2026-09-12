import { TestBed } from '@angular/core/testing';
import { TaskLocalDay } from './task-local-day';
import { TaskDueIndicator } from './task-due-indicator';

describe('Local calendar day and due indicators', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 12, 23, 59, 59));
    TestBed.configureTestingModule({ providers: [TaskLocalDay] });
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });
  it('refreshes at local midnight and schedules the next day', () => {
    const day = TestBed.inject(TaskLocalDay);
    expect(day.today()).toBe('2026-09-12');
    vi.advanceTimersByTime(1000);
    expect(day.today()).toBe('2026-09-13');
    vi.advanceTimersByTime(24 * 60 * 60 * 1000);
    expect(day.today()).toBe('2026-09-14');
  });
  it.each(['focus', 'visibilitychange'])(
    'refreshes after clock changes or sleep on %s',
    (event) => {
      const day = TestBed.inject(TaskLocalDay);
      vi.setSystemTime(new Date(2026, 8, 15, 8));
      (event === 'focus' ? window : document).dispatchEvent(new Event(event));
      expect(day.today()).toBe('2026-09-15');
      expect(vi.getTimerCount()).toBe(1);
    },
  );
  it('cleans up its timer and event listeners on destruction', () => {
    const day = TestBed.inject(TaskLocalDay);
    TestBed.resetTestingModule();
    expect(vi.getTimerCount()).toBe(0);
    vi.setSystemTime(new Date(2026, 8, 15));
    window.dispatchEvent(new Event('focus'));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(day.today()).toBe('2026-09-12');
    expect(vi.getTimerCount()).toBe(0);
  });
  it.each([
    ['2026-09-11', 'Overdue'],
    ['2026-09-12', 'Due today'],
    ['2026-09-13', 'Due'],
  ])('renders %s with explicit state %s and semantic date', (date, label) => {
    const fixture = TestBed.createComponent(TaskDueIndicator);
    fixture.componentRef.setInput('dueDate', date);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(label);
    expect(fixture.nativeElement.querySelector('time').getAttribute('datetime')).toBe(date);
    expect(fixture.nativeElement.querySelector('time').textContent.trim()).not.toBe('');
    if (label === 'Due today') expect(fixture.nativeElement.textContent).not.toContain('Overdue');
  });
  it('omits null card metadata but can announce no due date in details', () => {
    const fixture = TestBed.createComponent(TaskDueIndicator);
    fixture.componentRef.setInput('dueDate', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    fixture.componentRef.setInput('showEmpty', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No due date');
  });
  it('recomputes a rendered due-today indicator after midnight', () => {
    const fixture = TestBed.createComponent(TaskDueIndicator);
    fixture.componentRef.setInput('dueDate', '2026-09-12');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Due today');
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Overdue');
  });
});
