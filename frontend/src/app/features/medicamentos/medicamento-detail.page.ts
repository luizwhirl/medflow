import { Component, input, numberAttribute } from '@angular/core';

import { RastreioMedicamento } from './rastreio-medicamento';

@Component({
  selector: 'app-medicamento-detail-page',
<<<<<<< HEAD
  imports: [RouterLink, StatusBadge, SyncIndicator],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando…</p>
    } @else if (!e.medicamento) {
      <p class="feedback-error">Medicamento não encontrado.</p>
    } @else {
      <section class="surface-card p-5">
        <h1 class="text-xl font-bold">{{ e.medicamento.nome }} {{ e.medicamento.concentracao }}</h1>
        <p class="body-muted">
          {{ e.medicamento.formaFarmaceutica }} · {{ e.medicamento.categoria }}
        </p>
        <div class="mt-2 flex gap-2 text-xs font-semibold">
          @if (e.medicamento.rename) {
            <span class="rounded-md bg-brand-50 px-2 py-1 text-brand-600">RENAME</span>
          }
          <span class="rounded-md bg-canvas px-2 py-1 text-content-muted"
            >CATMAT {{ e.medicamento.catmat }}</span
          >
        </div>
      </section>

      <h2 class="mb-2 mt-5 font-bold">Onde encontrar</h2>
      <ul class="surface-list">
        @for (d of e.disponibilidade; track d.ubs.id) {
          <li>
            <a
              [routerLink]="['/ubs', d.ubs.id]"
              class="interactive-row flex items-center gap-3 px-4 py-3"
            >
              <div class="min-w-0 flex-1">
                <div class="truncate font-semibold">{{ d.ubs.nome }}</div>
                <div class="body-muted">{{ d.ubs.bairro }} · {{ d.quantidade }} un.</div>
                <app-sync-indicator class="mt-1 block" [em]="d.ubs.ultimaSincronizacao" />
              </div>
              <app-status-badge [status]="d.status" />
            </a>
          </li>
        } @empty {
          <li class="body-muted p-4">Nenhuma unidade informou estoque deste item.</li>
        }
      </ul>
    }
  `,
=======
  imports: [RastreioMedicamento],
  template: `<app-rastreio-medicamento [id]="id()" />`,
>>>>>>> 8398728c03490268b2efa8765da3ee142c40385d
})
export class MedicamentoDetailPage {
  /** Vem do parâmetro de rota `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });
}
