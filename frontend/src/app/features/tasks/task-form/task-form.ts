import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, submit, validate } from '@angular/forms/signals';
import { priorityLabels, taskPriorities } from '../task-priority';
import { Task, TaskPriority, UpdateTaskRequest } from '../task.models';
import { TaskField, TaskMutationResult } from '../task-management';

@Component({
  selector: 'app-task-form',
  imports: [FormField],
  templateUrl: './task-form.html',
  styleUrl: '../task-controls.scss',
})
export class TaskForm implements OnInit {
  readonly priorityLabels = priorityLabels;
  readonly priorities = taskPriorities;
  readonly inputId = input.required<string>();
  readonly action = input.required<string>();
  readonly initialTask = input<Task | null>(null);
  readonly disabled = input(false);
  readonly save = input.required<(request: UpdateTaskRequest) => Promise<TaskMutationResult>>();
  readonly closed = output<void>();
  private readonly destroyRef = inject(DestroyRef);
  private readonly titleInput = viewChild.required<ElementRef<HTMLInputElement>>('titleInput');
  private readonly model = signal({
    title: '',
    description: '',
    priority: 'MEDIUM' as TaskPriority,
    dueDate: '',
  });
  readonly serverError = signal('');
  readonly serverFields = signal<Partial<Record<TaskField, string>>>({});
  readonly taskForm = form(this.model, (path) => {
    required(path.title, { message: 'Enter a task title.' });
    maxLength(path.title, 200, { message: 'Use no more than 200 characters.' });
    validate(path.title, ({ value }) =>
      value().length > 0 && !value().trim()
        ? { kind: 'blank', message: 'Enter a non-blank task title.' }
        : null,
    );
    maxLength(path.description, 4000, { message: 'Use no more than 4000 characters.' });
    validate(path.priority, ({ value }) =>
      ['LOW', 'MEDIUM', 'HIGH'].includes(value())
        ? null
        : { kind: 'priority', message: 'Choose LOW, MEDIUM, or HIGH.' },
    );
    validate(path.dueDate, ({ value }) =>
      !value() || /^\d{4}-\d{2}-\d{2}$/.test(value())
        ? null
        : { kind: 'date', message: 'Enter a date in YYYY-MM-DD format.' },
    );
  });

  constructor() {
    afterNextRender(() => this.titleInput().nativeElement.focus());
  }

  ngOnInit(): void {
    const task = this.initialTask();
    if (task)
      this.model.set({
        title: task.title,
        description: task.description ?? '',
        priority: task.priority,
        dueDate: task.dueDate ?? '',
      });
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.disabled() || this.taskForm().submitting()) return;
    this.serverError.set('');
    this.serverFields.set({});
    await submit(this.taskForm, async (field) => {
      const value = field().value();
      const result = await this.save()({
        title: value.title.trim(),
        description: value.description.trim() || null,
        priority: value.priority,
        dueDate: value.dueDate || null,
      });
      if (this.destroyRef.destroyed) return;
      if (result.success || result.stale) this.closed.emit();
      else {
        this.serverError.set(result.error);
        this.serverFields.set(result.fields ?? {});
      }
    });
  }
}
