import { Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ColumnManagement } from '../column-management';
import { Column } from '../column.models';
import { ColumnNameForm } from '../column-name-form/column-name-form';

export type ColumnDialogData = { mutations: ColumnManagement } & (
  { kind: 'create' } | { kind: 'rename' | 'delete'; column: Column }
);

@Component({
  selector: 'app-column-dialog',
  imports: [ColumnNameForm],
  template: `
    @if (data.kind === 'delete') {
      <div class="confirmation" [attr.aria-busy]="deleting()">
        <p class="column-name">“{{ data.column.name }}” will be deleted.</p>
        <p class="errors" role="alert">{{ deleteError() }}</p>
        <div class="actions tf-dialog-actions">
          <button type="button" data-column-cancel [disabled]="deleting()" (click)="ref.close()">
            Cancel
          </button>
          <button class="danger" type="button" [disabled]="deleting()" (click)="deleteColumn()">
            {{ deleting() ? 'Deleting…' : 'Delete' }}
          </button>
        </div>
      </div>
      <p class="progress" role="status">{{ deleting() ? 'Deleting column…' : '' }}</p>
    } @else {
      <app-column-name-form
        [inputId]="
          data.kind === 'create' ? 'create-column-name' : 'rename-column-' + data.column.id
        "
        [action]="data.kind === 'create' ? 'Create column' : 'Save name'"
        [initialName]="data.kind === 'create' ? '' : data.column.name"
        [save]="save"
        (closed)="ref.close()"
      />
    }
  `,
  styleUrl: './column-dialog.scss',
})
export class ColumnDialog {
  readonly data = inject<ColumnDialogData>(DIALOG_DATA);
  readonly ref = inject(DialogRef);
  readonly deleting = signal(false);
  readonly deleteError = signal('');
  readonly save = async (name: string) => {
    this.ref.disableClose = true;
    try {
      return await (this.data.kind === 'create'
        ? this.data.mutations.create(name)
        : this.data.mutations.rename(this.data.column.id, name));
    } finally {
      this.ref.disableClose = false;
    }
  };

  async deleteColumn(): Promise<void> {
    if (this.data.kind !== 'delete' || this.deleting()) return;
    this.deleting.set(true);
    this.ref.disableClose = true;
    this.deleteError.set('');
    const result = await this.data.mutations.delete(this.data.column.id);
    this.deleting.set(false);
    this.ref.disableClose = false;
    if (result.success || result.stale) this.ref.close();
    else this.deleteError.set(result.error);
  }
}
