import { NgComponentOutlet } from '@angular/common';
import { Component, inject, Injectable, Injector, signal, Type } from '@angular/core';
import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { Icon } from '../icon/icon';
import { IconButton } from '../icon/icon-button';

export interface TaskflowDialogOptions<D> {
  title: string;
  size?: 'sm' | 'md' | 'lg';
  data?: D;
  disableClose?: boolean;
  autoFocus?: string;
  restoreFocus?: HTMLElement;
  injector?: Injector;
}

interface FrameData {
  title: string;
  content: Type<unknown>;
  data: unknown;
}

@Component({
  selector: 'app-dialog-frame',
  imports: [NgComponentOutlet, Icon, IconButton],
  template: `
    <header class="tf-dialog-header">
      <h2 tabindex="-1">{{ title() }}</h2>
      <button appIconButton="Close dialog" [disabled]="ref.disableClose" (click)="dismiss()">
        <app-icon name="close" />
      </button>
    </header>
    <div class="tf-dialog-content">
      <ng-container *ngComponentOutlet="frame.content; injector: contentInjector" />
    </div>
  `,
  host: { class: 'tf-dialog-frame' },
})
export class DialogFrame {
  readonly frame = inject<FrameData>(DIALOG_DATA);
  readonly title = signal(this.frame.title);
  readonly ref = inject(DialogRef);
  readonly contentInjector = Injector.create({
    parent: inject(Injector),
    providers: [{ provide: DIALOG_DATA, useValue: this.frame.data }],
  });

  dismiss(): void {
    if (!this.ref.disableClose) this.ref.close();
  }
}

@Injectable({ providedIn: 'root' })
export class TaskflowDialog {
  private readonly dialog = inject(Dialog);
  private readonly overlay = inject(Overlay);

  open<R = unknown, D = unknown>(
    content: Type<unknown>,
    options: TaskflowDialogOptions<D>,
  ): DialogRef<R, DialogFrame> {
    return this.dialog.open<R, FrameData, DialogFrame>(DialogFrame, {
      data: { title: options.title, content, data: options.data },
      injector: options.injector,
      ariaLabel: options.title,
      ariaModal: true,
      autoFocus: options.autoFocus ?? 'first-heading',
      restoreFocus: options.restoreFocus ?? true,
      disableClose: options.disableClose ?? false,
      hasBackdrop: true,
      backdropClass: 'tf-dialog-backdrop',
      panelClass: ['tf-dialog-panel', `tf-dialog-${options.size ?? 'md'}`],
      scrollStrategy: this.overlay.scrollStrategies.block(),
      disableAnimations: true,
    });
  }
}
