import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { Disponibilidade } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';
import { List } from '../../shared/components/list';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado =
  | { carregando: true }
  | { carregando: false; medicamento: Medicamento | null; disponibilidade: Disponibilidade[] };

@Component({
  selector: 'app-medicamento-detail-page',
  imports: [RouterLink, StatusBadge, SyncIndicator, Card, List, Button],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando…</p>
    } @else if (!e.medicamento) {
      <p class="feedback-error">Medicamento não encontrado.</p>
    } @else {
      <app-card>
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
        <button
          appButton
          type="button"
          [variante]="continuo() ? 'primario' : 'secundario'"
          [attr.aria-pressed]="continuo()"
          (click)="preferencias.alternarRemedioContinuo(id())"
          class="mt-4"
        >
          {{ continuo() ? 'Uso contínuo ✓' : 'Uso contínuo' }}
        </button>
      </app-card>

      <h2 class="mb-2 mt-5 font-bold">Onde encontrar</h2>
      <app-list
        [vazia]="e.disponibilidade.length === 0"
        vazio="Nenhuma unidade informou estoque deste item."
      >
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
        }
      </app-list>
    }
  `,
})
export class MedicamentoDetailPage {
  private readonly medicamentoService = inject(MedicamentoService);

  protected readonly preferencias = inject(PreferenciasService);

  readonly id = input.required({ transform: numberAttribute });
  protected readonly continuo = computed(() =>
    this.preferencias.remediosContinuos().includes(this.id()),
  );

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
