import { Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { Board } from '../board.models';
import { BoardListState } from '../board-list/board-list-state';
import { BoardNameForm } from '../board-name-form/board-name-form';

export type BoardDialogData = { state: BoardListState } & (
  { kind: 'create' } | { kind: 'rename' | 'delete'; board: Board }
);

@Component({
  selector: 'app-board-dialog',
  imports: [BoardNameForm],
  template: `
    @if (data.kind === 'delete') {
      <div class="confirmation" [attr.aria-busy]="deleting()">
        <p class="board-name">“{{ data.board.name }}”</p>
        <p>This will permanently delete the board and its contents.</p>
        <p>This action cannot be undone.</p>
        <p class="errors" role="alert">{{ deleteError() }}</p>
        <div class="actions tf-dialog-actions">
          <button type="button" data-board-cancel [disabled]="deleting()" (click)="ref.close()">
            Cancel
          </button>
          <button class="danger" type="button" [disabled]="deleting()" (click)="deleteBoard()">
            {{ deleting() ? 'Deleting…' : 'Delete board' }}
          </button>
        </div>
      </div>
      <p class="progress" role="status">{{ deleting() ? 'Deleting board…' : '' }}</p>
    } @else {
      <app-board-name-form
        [inputId]="data.kind === 'create' ? 'create-name' : 'rename-' + data.board.id"
        [action]="data.kind === 'create' ? 'Create board' : 'Save name'"
        [initialName]="data.kind === 'create' ? '' : data.board.name"
        [save]="save"
        (closed)="ref.close()"
      />
    }
  `,
  styleUrl: './board-dialog.scss',
})
export class BoardDialog {
  readonly data = inject<BoardDialogData>(DIALOG_DATA);
  readonly ref = inject(DialogRef);
  readonly deleting = signal(false);
  readonly deleteError = signal('');
  readonly save = async (name: string) => {
    this.ref.disableClose = true;
    try {
      return await (this.data.kind === 'create'
        ? this.data.state.create(name)
        : this.data.state.rename(this.data.board.id, name));
    } finally {
      this.ref.disableClose = false;
    }
  };

  async deleteBoard(): Promise<void> {
    if (this.data.kind !== 'delete' || this.deleting()) return;
    this.deleting.set(true);
    this.ref.disableClose = true;
    this.deleteError.set('');
    const result = await this.data.state.delete(this.data.board.id);
    this.deleting.set(false);
    this.ref.disableClose = false;
    if (result.success || result.stale) this.ref.close();
    else this.deleteError.set(result.error);
  }
}
