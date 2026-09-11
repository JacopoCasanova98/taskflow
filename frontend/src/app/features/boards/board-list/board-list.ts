import { Component, effect, inject, OnInit, signal } from '@angular/core';
import { BoardNameForm } from '../board-name-form/board-name-form';
import { Board } from '../board.models';
import { BoardListState } from './board-list-state';

@Component({
  selector: 'app-board-list',
  imports: [BoardNameForm],
  providers: [BoardListState],
  templateUrl: './board-list.html',
  styleUrls: ['../board-controls.scss', './board-list.scss'],
})
export class BoardList implements OnInit {
  readonly state = inject(BoardListState);
  readonly creating = signal(false);
  readonly editing = signal<Board | null>(null);
  readonly confirming = signal<Board | null>(null);
  readonly deleting = signal(false);
  readonly deleteError = signal('');
  readonly create = (name: string) => this.state.create(name);
  readonly rename = (name: string) => this.state.rename(this.editing()!.id, name);

  constructor() {
    effect(() => {
      if (this.state.loading() || this.state.loadError()) return;
      const ids = this.state.boards().map((board) => board.id);
      const editing = this.editing();
      const confirming = this.confirming();
      if (editing && !ids.includes(editing.id)) this.editing.set(null);
      if (confirming && !ids.includes(confirming.id)) this.confirming.set(null);
    });
  }

  ngOnInit(): void {
    void this.state.load();
  }

  confirmDelete(board: Board): void {
    if (this.deleting()) return;
    this.deleteError.set('');
    this.confirming.set(board);
  }

  async deleteBoard(): Promise<void> {
    const board = this.confirming();
    if (!board || this.deleting()) return;
    this.deleting.set(true);
    this.deleteError.set('');
    const result = await this.state.delete(board.id);
    this.deleting.set(false);
    if (result.success || result.stale) this.confirming.set(null);
    else this.deleteError.set(result.error);
  }
}
