import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { DialogRef } from '@angular/cdk/dialog';
import { DialogFrame, TaskflowDialog } from '../../../shared/dialog/taskflow-dialog';
import { Icon } from '../../../shared/icon/icon';
import { IconButton } from '../../../shared/icon/icon-button';
import { BoardDialog, BoardDialogData } from '../board-dialog/board-dialog';
import { Board } from '../board.models';
import { BoardListState } from './board-list-state';

@Component({
  selector: 'app-board-list',
  imports: [RouterLink, CdkMenu, CdkMenuItem, CdkMenuTrigger, Icon, IconButton],
  providers: [BoardListState],
  templateUrl: './board-list.html',
  styleUrl: './board-list.scss',
})
export class BoardList implements OnInit, OnDestroy {
  readonly state = inject(BoardListState);
  private readonly dialogs = inject(TaskflowDialog);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly createButton = viewChild<ElementRef<HTMLButtonElement>>('createButton');
  private readonly active = signal<{
    ref: DialogRef<unknown, DialogFrame>;
    boardId?: string;
  } | null>(null);

  constructor() {
    effect(() => {
      const active = this.active();
      if (this.state.loading() || this.state.loadError() || !active?.boardId) return;
      if (
        !this.state.boards().some((board) => board.id === active.boardId) &&
        !active.ref.disableClose
      ) {
        active.ref.close();
      }
    });
  }

  ngOnInit(): void {
    void this.state.load();
  }
  ngOnDestroy(): void {
    this.active()?.ref.close();
  }

  openCreate(returnFocus: HTMLElement): void {
    this.open({ kind: 'create', state: this.state }, returnFocus);
  }
  openRename(board: Board, returnFocus: HTMLElement): void {
    this.open({ kind: 'rename', board, state: this.state }, returnFocus);
  }
  openDelete(board: Board, returnFocus: HTMLElement): void {
    this.open({ kind: 'delete', board, state: this.state }, returnFocus);
  }

  private open(data: BoardDialogData, returnFocus: HTMLElement): void {
    if (this.active()) return;
    const ref = this.dialogs.open(BoardDialog, {
      title:
        data.kind === 'create'
          ? 'Create board'
          : data.kind === 'rename'
            ? 'Rename board'
            : 'Delete board?',
      size: data.kind === 'delete' ? 'sm' : 'md',
      autoFocus: data.kind === 'delete' ? '[data-board-cancel]' : 'input',
      restoreFocus: returnFocus,
      data,
    });
    this.active.set({ ref, boardId: data.kind === 'create' ? undefined : data.board.id });
    ref.closed.subscribe(() => {
      this.active.set(null);
      if (this.destroyRef.destroyed) return;
      afterNextRender(
        () => {
          const fallback = this.createButton()?.nativeElement;
          if (!returnFocus.isConnected && fallback?.isConnected) fallback.focus();
        },
        { injector: this.injector },
      );
    });
  }
}
