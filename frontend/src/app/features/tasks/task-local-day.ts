import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { localCalendarDate } from './task-due-date';

/** One timer per Board page; recalculate after sleep/backgrounding as well as local midnight. */
@Injectable()
export class TaskLocalDay {
  private readonly day = signal(localCalendarDate());
  readonly today = this.day.asReadonly();
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    const document = inject(DOCUMENT);
    const window = document.defaultView;
    const refresh = () => {
      if (this.timer !== undefined) clearTimeout(this.timer);
      const now = new Date();
      this.day.set(localCalendarDate(now));
      const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      this.timer = setTimeout(refresh, midnight.getTime() - now.getTime());
    };
    refresh();
    document.addEventListener('visibilitychange', refresh);
    window?.addEventListener('focus', refresh);
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.timer);
      document.removeEventListener('visibilitychange', refresh);
      window?.removeEventListener('focus', refresh);
    });
  }
}
