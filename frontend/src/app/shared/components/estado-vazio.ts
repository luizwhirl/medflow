import { Component, input } from '@angular/core';

/** Ícones de traço (viewBox 24) usados nos estados vazios. */
export const ICONES_VAZIO = {
  busca: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  local:
    'M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  marcador: 'M6 3h12v18l-6-4-6 4z',
  pilula: 'M10.5 20.5 3.5 13.5a4.95 4.95 0 0 1 7-7l7 7a4.95 4.95 0 0 1-7 7zM7 10l7 7',
  caixa: 'M3 7l9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4M12 11v10',
} as const;

/**
 * Estado vazio padrão: ícone, título, explicação e (projetada) a ação para sair dele.
 * `compacto` serve dentro de um cartão ou lista que já tem moldura.
 */
@Component({
  selector: 'app-estado-vazio',
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-col items-center text-center"
      [class]="compacto() ? 'px-4 py-6' : 'surface-card p-8 sm:p-10'"
    >
      <span
        class="grid place-items-center rounded-full bg-brand-50 text-brand-600"
        [class]="compacto() ? 'size-10' : 'size-14'"
      >
        <svg
          [class]="compacto() ? 'size-5' : 'size-7'"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path [attr.d]="icone()" />
        </svg>
      </span>
      <p class="font-bold text-content" [class]="compacto() ? 'mt-3' : 'mt-4 text-lg'">
        {{ titulo() }}
      </p>
      @if (texto()) {
        <p class="mt-1 max-w-sm text-content-muted" [class.text-sm]="compacto()">{{ texto() }}</p>
      }
      <div class="empty:hidden mt-5 flex flex-wrap justify-center gap-3"><ng-content /></div>
    </div>
  `,
})
export class EstadoVazio {
  readonly titulo = input.required<string>();
  readonly texto = input('');
  readonly icone = input<string>(ICONES_VAZIO.busca);
  readonly compacto = input(false);
}
