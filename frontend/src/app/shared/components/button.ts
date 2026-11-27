import { Directive, input } from '@angular/core';

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-control transition-colors disabled:cursor-not-allowed disabled:opacity-50';

const TAMANHOS = {
  md: 'h-12 px-5 font-bold',
  /** Densidade do desktop de gestão (Figma: 44 px, texto 14 semibold). */
  sm: 'h-11 px-4 text-sm font-semibold',
} as const;

const VARIANTES = {
  primario: {
    md: 'bg-brand-600 text-white hover:bg-brand-700',
    sm: 'bg-brand-600 text-white hover:bg-brand-700',
  },
  secundario: {
    md: 'border border-outline bg-surface text-content hover:bg-canvas',
    sm: 'border border-outline-strong bg-surface text-content hover:bg-canvas',
  },
  fantasma: { md: 'text-brand-600 hover:bg-brand-50', sm: 'text-brand-600 hover:bg-brand-50' },
} as const;

export type VarianteBotao = keyof typeof VARIANTES;
export type TamanhoBotao = keyof typeof TAMANHOS;

/** Estilo de botão: `<button appButton variante="secundario">` (também serve em `<a>`). */
@Directive({
  selector: 'button[appButton], a[appButton]',
  host: { '[class]': 'classes()' },
})
export class Button {
  readonly variante = input<VarianteBotao>('primario');
  readonly tamanho = input<TamanhoBotao>('md');
  protected classes(): string {
    return `${BASE} ${TAMANHOS[this.tamanho()]} ${VARIANTES[this.variante()][this.tamanho()]}`;
  }
}
