import { Directive, input } from '@angular/core';

@Directive({
  selector: 'button[appIconButton]',
  host: {
    class: 'tf-icon-button',
    type: 'button',
    '[attr.aria-label]': 'appIconButton()',
    '[attr.title]': 'appIconButton()',
    '[class.tf-icon-button-danger]': 'danger()',
  },
})
export class IconButton {
  readonly appIconButton = input.required<string>();
  readonly danger = input(false);
}
