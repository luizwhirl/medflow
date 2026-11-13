import { Component, model } from '@angular/core';

/** Chip de filtro alternável (ex.: categoria, raio). */
@Component({
  selector: 'app-chip',
  template: `
    <button
      type="button"
      [attr.aria-pressed]="selecionado()"
      (click)="selecionado.set(!selecionado())"
      class="inline-flex h-9 items-center whitespace-nowrap rounded-full border px-3.5 text-sm font-semibold transition-colors"
      [class]="
        selecionado()
          ? 'border-brand-600 bg-brand-600 text-white'
          : 'border-outline bg-surface text-content-muted hover:bg-canvas'
      "
    >
      <ng-content />
    </button>
  `,
})
export class Chip {
  readonly selecionado = model(false);
}
