import { Component, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { Disponibilidade } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado =
  | { carregando: true }
  | { carregando: false; medicamento: Medicamento | null; disponibilidade: Disponibilidade[] };

@Component({
  selector: 'app-medicamento-detail-page',
  imports: [RouterLink, StatusBadge, SyncIndicator],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="text-slate-500">Carregando…</p>
    } @else if (!e.medicamento) {
      <p class="rounded-xl bg-red-50 p-4 text-sm text-red-800">Medicamento não encontrado.</p>
    } @else {
      <section class="rounded-2xl bg-white p-5 shadow-sm">
        <h1 class="text-xl font-bold">{{ e.medicamento.nome }} {{ e.medicamento.concentracao }}</h1>
        <p class="text-sm text-slate-600">
          {{ e.medicamento.formaFarmaceutica }} · {{ e.medicamento.categoria }}
        </p>
        <div class="mt-2 flex gap-2 text-xs font-semibold">
          @if (e.medicamento.rename) {
            <span class="rounded-md bg-brand-50 px-2 py-1 text-brand-600">RENAME</span>
          }
          <span class="rounded-md bg-slate-100 px-2 py-1 text-slate-600"
            >CATMAT {{ e.medicamento.catmat }}</span
          >
        </div>
      </section>

      <h2 class="mb-2 mt-5 font-bold">Onde encontrar</h2>
      <ul class="divide-y divide-slate-200 overflow-hidden rounded-2xl bg-white shadow-sm">
        @for (d of e.disponibilidade; track d.ubs.id) {
          <li>
            <a
              [routerLink]="['/ubs', d.ubs.id]"
              class="flex items-center gap-3 px-4 py-3 hover:bg-slate-50"
            >
              <div class="min-w-0 flex-1">
                <div class="truncate font-semibold">{{ d.ubs.nome }}</div>
                <div class="text-sm text-slate-500">
                  {{ d.ubs.bairro }} · {{ d.quantidade }} un.
                </div>
                <app-sync-indicator class="mt-1 block" [em]="d.ubs.ultimaSincronizacao" />
              </div>
              <app-status-badge [status]="d.status" />
            </a>
          </li>
        } @empty {
          <li class="p-4 text-slate-500">Nenhuma unidade informou estoque deste item.</li>
        }
      </ul>
    }
  `,
})
export class MedicamentoDetailPage {
  private readonly medicamentoService = inject(MedicamentoService);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly estado = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        forkJoin({
          medicamento: this.medicamentoService.buscarPorId(id),
          disponibilidade: this.medicamentoService.disponibilidade(id),
        }).pipe(
          map(({ medicamento, disponibilidade }): Estado => ({
            carregando: false,
            medicamento,
            disponibilidade,
          })),
          catchError(() =>
            of<Estado>({ carregando: false, medicamento: null, disponibilidade: [] }),
          ),
          startWith<Estado>({ carregando: true }),
        ),
      ),
    ),
    { initialValue: { carregando: true } as Estado },
  );
}
