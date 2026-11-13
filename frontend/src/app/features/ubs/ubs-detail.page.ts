import { Component, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { ItemEstoque } from '../../core/models/estoque.model';
import { Ubs } from '../../core/models/ubs.model';
import { UbsService } from '../../core/services/ubs.service';
import { Card } from '../../shared/components/card';
import { List } from '../../shared/components/list';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado = { carregando: true } | { carregando: false; ubs: Ubs | null; estoque: ItemEstoque[] };

@Component({
  selector: 'app-ubs-detail-page',
  imports: [StatusBadge, SyncIndicator, Card, List],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando…</p>
    } @else if (!e.ubs) {
      <p class="feedback-error">Unidade não encontrada.</p>
    } @else {
      <app-card>
        <div class="flex flex-wrap gap-2 text-xs font-semibold text-content-muted">
          <span>{{ e.ubs.tipo }}</span
          ><span>CNES {{ e.ubs.cnes }}</span>
        </div>
        <h1 class="mt-1 text-xl font-bold">{{ e.ubs.nome }}</h1>
        <p class="body-muted">{{ e.ubs.endereco }} · {{ e.ubs.bairro }}</p>
        @if (e.ubs.horario) {
          <p class="body-muted">{{ e.ubs.horario }}</p>
        }
        <app-sync-indicator class="mt-2 block" [em]="e.ubs.ultimaSincronizacao" />
      </app-card>

      <h2 class="mb-2 mt-5 font-bold">Estoque da farmácia</h2>
      <app-list [vazia]="e.estoque.length === 0" vazio="Sem itens de estoque registrados.">
        @for (item of e.estoque; track item.medicamento.id) {
          <li class="flex items-center gap-3 px-4 py-3">
            <div class="min-w-0 flex-1">
              <div class="font-semibold">
                {{ item.medicamento.nome }} {{ item.medicamento.concentracao }}
              </div>
              <div class="body-muted">
                {{ item.medicamento.categoria }} · {{ item.quantidade }} un.
              </div>
            </div>
            <app-status-badge [status]="item.status" />
          </li>
        }
      </app-list>
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
