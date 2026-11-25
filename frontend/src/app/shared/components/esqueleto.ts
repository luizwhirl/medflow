import { Component, computed, input } from '@angular/core';

/**
 * - `cartao`: cartão com ícone e duas linhas (remédios, UBS próximas, Minha lista);
 * - `linha`: item de lista com título e detalhe (resultados da busca, UBS do mapa, estoque);
 * - `detalhe`: cabeçalho e dois cartões (detalhe do medicamento, da UBS e painel).
 */
export type FormatoEsqueleto = 'cartao' | 'linha' | 'detalhe';

/**
 * Esqueleto de carregamento: blocos cinza no formato do conteúdo que vai chegar, para a tela não
 * ficar em branco nem "pular" quando os dados aparecem. Os itens ficam direto no host, então quem
 * usa define a grade: `<app-esqueleto formato="cartao" class="grid gap-4 sm:grid-cols-3" />`.
 */
@Component({
  selector: 'app-esqueleto',
  // Sem rótulo (um segundo esqueleto na mesma tela), fica só visual, sem anunciar de novo.
  host: {
    '[attr.role]': 'rotulo() ? "status" : null',
    '[attr.aria-label]': 'rotulo() || null',
  },
  template: `
    @if (rotulo()) {
      <span class="sr-only">{{ rotulo() }}</span>
    }
    @switch (formato()) {
      @case ('cartao') {
        @for (i of itens(); track i) {
          <div class="surface-card p-6" aria-hidden="true">
            <div class="flex items-start justify-between gap-2">
              <div class="osso size-12 rounded-control"></div>
              <div class="osso h-6 w-20 rounded-full"></div>
            </div>
            <div class="osso mt-4 h-5 rounded-md w-3/4"></div>
            <div class="osso mt-3 h-4 rounded-md w-1/2"></div>
          </div>
        }
      }
      @case ('linha') {
        @for (i of itens(); track i) {
          <div class="rounded-card border border-outline bg-surface px-5 py-4" aria-hidden="true">
            <div class="osso h-5 rounded-md w-2/3"></div>
            <div class="osso mt-2.5 h-4 rounded-md w-2/5"></div>
          </div>
        }
      }
      @case ('detalhe') {
        <div aria-hidden="true">
          <div class="flex items-start gap-4">
            <div class="osso size-14 shrink-0 rounded-card"></div>
            <div class="min-w-0 flex-1">
              <div class="osso h-8 rounded-md w-2/3 max-w-md"></div>
              <div class="osso mt-3 h-4 rounded-md w-1/3 max-w-xs"></div>
            </div>
          </div>
          <div class="mt-6 grid gap-6 xl:grid-cols-2">
            @for (i of itens(); track i) {
              <div class="surface-card p-7">
                <div class="osso h-6 rounded-md w-1/2"></div>
                <div class="osso mt-5 h-4 rounded-md w-full"></div>
                <div class="osso mt-3 h-4 rounded-md w-5/6"></div>
                <div class="osso mt-3 h-4 rounded-md w-2/3"></div>
              </div>
            }
          </div>
        </div>
      }
    }
  `,
  styles: `
    /* Só cor e animação: o raio vem das classes rounded-* (estilos do componente ficam fora das
       camadas do Tailwind e venceriam qualquer utilitário). */
    .osso {
      background: var(--color-outline);
    }
    @media (prefers-reduced-motion: no-preference) {
      .osso {
        animation: pulsar 1.6s ease-in-out infinite;
      }
    }
    @keyframes pulsar {
      50% {
        opacity: 0.45;
      }
    }
  `,
})
export class Esqueleto {
  readonly formato = input<FormatoEsqueleto>('linha');
  readonly quantidade = input(3);
  /** Lido por leitores de tela. */
  readonly rotulo = input('Carregando…');

  protected readonly itens = computed(() => Array.from({ length: this.quantidade() }, (_, i) => i));
}
