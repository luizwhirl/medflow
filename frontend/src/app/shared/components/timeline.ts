import { Component, input } from '@angular/core';

/**
 * Linha do tempo de etapas. `atual` é o índice da última etapa concluída;
 * `null` significa que não sabemos em que etapa está.
 */
@Component({
  selector: 'app-timeline',
  template: `
    <ol class="flex flex-col gap-0 md:flex-row">
      @for (etapa of etapas(); track etapa; let i = $index, ultimo = $last) {
        @let feito = atual() !== null && i <= atual()!;
        <li
          class="relative flex gap-3 pb-4 md:flex-1 md:flex-col md:items-center md:pb-0 md:text-center"
          [attr.aria-current]="atual() === i ? 'step' : null"
        >
          @if (!ultimo) {
            <span
              class="absolute left-[0.6875rem] top-6 h-full w-0.5 md:left-1/2 md:top-[0.6875rem] md:h-0.5 md:w-full"
              [class]="atual() !== null && i < atual()! ? 'bg-brand-600' : 'bg-outline'"
              aria-hidden="true"
            ></span>
          }
          <span
            class="relative z-[1] grid size-6 shrink-0 place-items-center rounded-full border-2 text-xs font-bold"
            [class]="
              feito ? 'border-brand-600 bg-brand-600 text-white' : 'border-outline bg-surface'
            "
            aria-hidden="true"
            >{{ feito ? '✓' : '' }}</span
          >
          <span
            class="text-sm md:mt-2"
            [class]="feito ? 'font-semibold text-content' : 'text-content-muted'"
          >
            {{ etapa }}<span class="sr-only">{{ feito ? ' (concluída)' : ' (pendente)' }}</span>
          </span>
        </li>
      }
    </ol>
  `,
})
export class Timeline {
  readonly etapas = input.required<readonly string[]>();
  readonly atual = input<number | null>(null);
}
