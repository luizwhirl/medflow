import { Component, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { ItemEstoque } from '../../core/models/estoque.model';
import { Ubs } from '../../core/models/ubs.model';
import { UbsService } from '../../core/services/ubs.service';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado = { carregando: true } | { carregando: false; ubs: Ubs | null; estoque: ItemEstoque[] };

@Component({
  selector: 'app-ubs-detail-page',
  imports: [StatusBadge, SyncIndicator],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="text-slate-500">Carregando…</p>
    } @else if (!e.ubs) {
      <p class="rounded-xl bg-red-50 p-4 text-sm text-red-800">Unidade não encontrada.</p>
    } @else {
      <section class="rounded-2xl bg-white p-5 shadow-sm">
        <div class="flex flex-wrap gap-2 text-xs font-semibold text-slate-500">
          <span>{{ e.ubs.tipo }}</span
          ><span>CNES {{ e.ubs.cnes }}</span>
        </div>
        <h1 class="mt-1 text-xl font-bold">{{ e.ubs.nome }}</h1>
        <p class="text-sm text-slate-600">{{ e.ubs.endereco }} · {{ e.ubs.bairro }}</p>
        @if (e.ubs.horario) {
          <p class="text-sm text-slate-600">{{ e.ubs.horario }}</p>
        }
        <app-sync-indicator class="mt-2 block" [em]="e.ubs.ultimaSincronizacao" />
      </section>

      <h2 class="mb-2 mt-5 font-bold">Estoque da farmácia</h2>
      <ul class="divide-y divide-slate-200 overflow-hidden rounded-2xl bg-white shadow-sm">
        @for (item of e.estoque; track item.medicamento.id) {
          <li class="flex items-center gap-3 px-4 py-3">
            <div class="min-w-0 flex-1">
              <div class="font-semibold">
                {{ item.medicamento.nome }} {{ item.medicamento.concentracao }}
              </div>
              <div class="text-sm text-slate-500">
                {{ item.medicamento.categoria }} · {{ item.quantidade }} un.
              </div>
            </div>
            <app-status-badge [status]="item.status" />
          </li>
        } @empty {
          <li class="p-4 text-slate-500">Sem itens de estoque registrados.</li>
        }
      </ul>
    }
  `,
})
export class UbsDetailPage {
  private readonly ubsService = inject(UbsService);

  /** Vem do parâmetro de rota `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly estado = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        forkJoin({
          ubs: this.ubsService.buscarPorId(id),
          estoque: this.ubsService.estoque(id),
        }).pipe(
          map(({ ubs, estoque }): Estado => ({ carregando: false, ubs, estoque })),
          catchError(() => of<Estado>({ carregando: false, ubs: null, estoque: [] })),
          startWith<Estado>({ carregando: true }),
        ),
      ),
    ),
    { initialValue: { carregando: true } as Estado },
  );
}
