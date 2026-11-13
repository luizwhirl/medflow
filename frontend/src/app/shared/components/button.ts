import { Directive, input } from '@angular/core';

const BASE =
  'inline-flex h-12 items-center justify-center gap-2 rounded-control px-5 font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-50';

const VARIANTES = {
  primario: 'bg-brand-600 text-white hover:bg-brand-700',
  secundario: 'border border-outline bg-surface text-content hover:bg-canvas',
  fantasma: 'text-brand-600 hover:bg-brand-50',
} as const;

export type VarianteBotao = keyof typeof VARIANTES;

/** Estilo de botão: `<button appButton variante="secundario">` (também serve em `<a>`). */
@Directive({
  selector: 'button[appButton], a[appButton]',
  host: { '[class]': 'classes()' },
})
export class Button {
  readonly variante = input<VarianteBotao>('primario');
  protected classes(): string {
    return `${BASE} ${VARIANTES[this.variante()]}`;
  }
}
