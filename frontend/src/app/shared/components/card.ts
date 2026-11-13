import { Component, input } from '@angular/core';

const PADDING = { none: '', sm: 'p-4', md: 'p-5', lg: 'p-6' } as const;

/** Superfície branca com sombra e cantos arredondados. */
@Component({
  selector: 'app-card',
  host: {
    class: 'block rounded-card border border-outline bg-surface shadow-card',
    '[class]': 'padding()',
  },
  template: '<ng-content />',
})
export class Card {
  readonly pad = input<keyof typeof PADDING>('md');
  protected padding(): string {
    return PADDING[this.pad()];
  }
}
