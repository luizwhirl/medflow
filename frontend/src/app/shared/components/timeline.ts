import { Component, input } from '@angular/core';

export interface EtapaTimeline {
  titulo: string;
  /** Linha secundária (data ou previsão), se houver. */
  detalhe?: string | null;
}

/**
 * Linha do tempo vertical (Figma: "Rastreamento da remessa").
 * `atual` é o índice da última etapa concluída; `null` = etapa desconhecida.
 */
@Component({
  selector: 'app-timeline',
  template: `
    <ol>
      @for (etapa of etapas(); track etapa.titulo; let i = $index, ultimo = $last) {
        @let feito = atual() !== null && i <= atual()!;
        @let corrente = atual() !== null && i === atual()! + 1;
        <li
          class="relative flex gap-4 pb-6 last:pb-0"
          [attr.aria-current]="atual() === i ? 'step' : null"
        >
          @if (!ultimo) {
            <span
              class="absolute left-[0.6875rem] top-6 h-[calc(100%-1.5rem)] w-0.5"
              [class]="atual() !== null && i < atual()! ? 'bg-brand-600' : 'bg-outline'"
              aria-hidden="true"
            ></span>
          }
          <span
            class="relative z-[1] mt-0.5 size-6 shrink-0 rounded-full border-[3px]"
            [class]="
              feito
                ? 'border-brand-600 bg-brand-600'
                : corrente
                  ? 'border-brand-600 bg-surface'
                  : 'border-outline bg-surface'
            "
            aria-hidden="true"
          ></span>
          <span>
            <span
              class="block text-lg font-semibold"
              [class]="feito || corrente ? 'text-content' : 'text-content-muted'"
              >{{ etapa.titulo
              }}<span class="sr-only">{{ feito ? ' (concluída)' : ' (pendente)' }}</span></span
            >
            @if (etapa.detalhe) {
              <span class="block text-sm text-content-muted">{{ etapa.detalhe }}</span>
            }
          </span>
        </li>
      }
    </ol>
  `,
})
export class Timeline {
  readonly etapas = input.required<readonly EtapaTimeline[]>();
  readonly atual = input<number | null>(null);
}
