import {
  afterNextRender,
  Component,
  ElementRef,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, submit, validate } from '@angular/forms/signals';
import { BoardMutationResult } from '../board-list/board-list-state';

@Component({
  selector: 'app-board-name-form',
  imports: [FormField],
  templateUrl: './board-name-form.html',
  styleUrl: '../board-controls.scss',
})
export class BoardNameForm implements OnInit {
  readonly inputId = input.required<string>();
  readonly action = input.required<string>();
  readonly initialName = input('');
  readonly save = input.required<(name: string) => Promise<BoardMutationResult>>();
  readonly closed = output<void>();
  private readonly nameInput = viewChild.required<ElementRef<HTMLInputElement>>('nameInput');
  private readonly model = signal({ name: '' });
  readonly serverError = signal('');
  readonly nameForm = form(this.model, (path) => {
    required(path.name, { message: 'Enter a board name.' });
    maxLength(path.name, 120, { message: 'Use no more than 120 characters.' });
    validate(path.name, ({ value }) =>
      value().length > 0 && !value().trim()
        ? { kind: 'blank', message: 'Enter a non-blank board name.' }
        : null,
    );
  });

  constructor() {
    afterNextRender(() => this.nameInput().nativeElement.focus());
  }

  ngOnInit(): void {
    this.model.set({ name: this.initialName() });
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.nameForm().submitting()) return;
    this.serverError.set('');
    await submit(this.nameForm, async (field) => {
      const result = await this.save()(field().value().name.trim());
      if (result.success || result.stale) this.closed.emit();
      else this.serverError.set(result.error);
    });
  }
}
