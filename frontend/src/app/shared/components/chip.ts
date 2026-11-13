import { Component, input, model } from '@angular/core';

const ESTILOS = {
  /** Filtro alternável em contorno; selecionado fica verde-claro (Figma). */
  filtro: {
    base: 'h-10 border px-4',
    ligado: 'border-brand-600 bg-brand-50 font-semibold text-brand-700',
    desligado: 'border-outline bg-surface text-content-muted hover:bg-canvas',
  },
  /** Sugestão (ex.: buscas recentes): pílula cinza, sem estado. */
  sugestao: {
    base: 'h-9 px-3.5',
    ligado: 'bg-canvas text-content',
    desligado: 'bg-canvas text-content hover:bg-outline',
  },
} as const;

/** Chip de filtro alternável (ex.: categoria, raio) ou de sugestão. */
@Component({
  selector: 'app-chip',
  template: `
    <button
      type="button"
      [attr.aria-pressed]="variante() === 'filtro' ? selecionado() : null"
      (click)="selecionado.set(!selecionado())"
      class="inline-flex items-center whitespace-nowrap rounded-full text-sm transition-colors"
      [class]="classes()"
    >
      <ng-content />
    </button>
  `,
})
export class Chip {
  readonly selecionado = model(false);
  readonly variante = input<keyof typeof ESTILOS>('filtro');

  protected classes(): string {
    const e = ESTILOS[this.variante()];
    return `${e.base} ${this.selecionado() ? e.ligado : e.desligado}`;
  }
}
