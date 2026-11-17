import { Component, input, model } from '@angular/core';

const ESTILOS = {
  /** Filtro alternável em contorno; selecionado fica verde-claro (Figma). */
  filtro: {
    base: 'h-10 border px-4 text-sm',
    ligado: 'border-brand-600 bg-brand-50 font-semibold text-brand-700',
    desligado: 'border-outline bg-surface text-content-muted hover:bg-canvas',
  },
  /** Filtro compacto do desktop de gestão (Figma: 36 px, texto 13). */
  'filtro-sm': {
    base: 'h-9 border px-3 text-[0.8125rem]',
    ligado: 'border-brand-600 bg-brand-50 font-semibold text-brand-700',
    desligado: 'border-outline-strong bg-surface text-content-muted hover:bg-canvas',
  },
  /** Sugestão (ex.: buscas recentes): pílula cinza, sem estado. */
  sugestao: {
    base: 'h-9 px-3.5 text-sm',
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
      [attr.aria-pressed]="variante() === 'sugestao' ? null : selecionado()"
      (click)="alternar()"
      class="inline-flex items-center whitespace-nowrap rounded-full transition-colors"
      [class]="classes()"
    >
      <ng-content />
    </button>
  `,
})
export class Chip {
  readonly selecionado = model(false);
  readonly variante = input<keyof typeof ESTILOS>('filtro');
  /** Se selecionado, não desmarca ao ser clicado (ex.: "Todos" num grupo de filtros exclusivos). */
  readonly fixo = input(false);

  protected alternar(): void {
    if (this.fixo() && this.selecionado()) return;
    this.selecionado.set(!this.selecionado());
  }

  protected classes(): string {
    const e = ESTILOS[this.variante()];
    return `${e.base} ${this.selecionado() ? e.ligado : e.desligado}`;
  }
}
