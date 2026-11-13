import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { Coordenadas, distanciaKm, posicaoAtual } from '../../core/geo';
import { Medicamento } from '../../core/models/medicamento.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';
import { List } from '../../shared/components/list';
import { SearchField } from '../../shared/components/search-field';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

interface RemedioContinuo {
  medicamento: Medicamento;
  /** Status na UBS de referência; sem referência, o melhor status entre as unidades. */
  status: StatusEstoque;
}

const ORDEM_STATUS: StatusEstoque[] = ['DISPONIVEL', 'RESERVADO', 'EM_FALTA'];

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, Card, SearchField, Button, List, StatusBadge, SyncIndicator],
  template: `
    <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_22rem] md:gap-6">
      <div class="flex min-w-0 flex-col gap-4 md:gap-6">
        <app-card pad="lg">
          <p class="body-muted">Consulta de estoque nas Unidades Básicas de Saúde</p>
          <h1 class="mt-1 text-2xl font-bold tracking-tight">Qual remédio você procura?</h1>
          <div class="mt-5 flex gap-2">
            <app-search-field
              class="min-w-0 flex-1"
              placeholder="Ex.: Metformina"
              [(valor)]="termo"
              (buscar)="buscar()"
            />
            <button appButton type="button" (click)="buscar()">Buscar</button>
          </div>
        </app-card>

        <section aria-labelledby="titulo-continuos">
          <h2 id="titulo-continuos" class="mb-2 font-bold">Meus remédios de uso contínuo</h2>
          <app-list
            [vazia]="continuos().length === 0"
            vazio="Nenhum remédio salvo. Abra um medicamento e toque em “Uso contínuo”."
          >
            @for (r of continuos(); track r.medicamento.id) {
              <li>
                <a
                  [routerLink]="['/medicamentos', r.medicamento.id]"
                  class="interactive-row flex items-center gap-3 px-4 py-3"
                >
                  <div class="min-w-0 flex-1">
                    <div class="truncate font-semibold">
                      {{ r.medicamento.nome }} {{ r.medicamento.concentracao }}
                    </div>
                    <div class="body-muted">{{ r.medicamento.categoria }}</div>
                  </div>
                  <app-status-badge [status]="r.status" />
                </a>
              </li>
            }
          </app-list>
        </section>

        @if (categorias().length) {
          <section aria-labelledby="titulo-atalhos">
            <h2 id="titulo-atalhos" class="mb-2 font-bold">Por condição</h2>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">
              @for (c of categorias(); track c) {
                <a
                  [routerLink]="['/medicamentos']"
                  [queryParams]="{ categoria: c }"
                  class="surface-card px-4 py-3 text-sm font-semibold transition-shadow hover:ring-2 hover:ring-brand-100"
                  >{{ c }}</a
                >
              }
            </div>
          </section>
        }
      </div>

      <aside class="flex min-w-0 flex-col gap-4 md:gap-6">
        <section aria-labelledby="titulo-referencia">
          <h2 id="titulo-referencia" class="mb-2 font-bold">Minha UBS de referência</h2>
          @if (ubsReferencia(); as u) {
            <app-card>
              <a [routerLink]="['/ubs', u.id]" class="font-semibold hover:underline">{{
                u.nome
              }}</a>
              <p class="body-muted">{{ u.endereco }} · {{ u.bairro }}</p>
              @if (u.horario) {
                <p class="body-muted">{{ u.horario }}</p>
              }
              <app-sync-indicator class="mt-2 block" [em]="u.ultimaSincronizacao" />
            </app-card>
          } @else {
            <app-card>
              <p class="body-muted">Você ainda não escolheu sua UBS.</p>
              <a appButton variante="secundario" routerLink="/mapa" class="mt-3 w-full"
                >Escolher UBS</a
              >
            </app-card>
          }
        </section>

        <section aria-labelledby="titulo-proximas">
          <h2 id="titulo-proximas" class="mb-2 font-bold">
            {{ posicao() ? 'UBS perto de você' : 'Unidades de saúde' }}
          </h2>
          <app-list [vazia]="proximas().length === 0" vazio="Nenhuma unidade encontrada.">
            @for (p of proximas(); track p.ubs.id) {
              <li>
                <a [routerLink]="['/ubs', p.ubs.id]" class="interactive-row px-4 py-3">
                  <div class="flex items-baseline gap-2">
                    <span class="min-w-0 flex-1 truncate font-semibold">{{ p.ubs.nome }}</span>
                    @if (p.distancia !== null) {
                      <span class="text-xs font-semibold text-content-muted"
                        >{{ p.distancia.toFixed(1) }} km</span
                      >
                    }
                  </div>
                  <div class="body-muted">{{ p.ubs.bairro }}</div>
                </a>
              </li>
            }
          </app-list>
          <a appButton variante="fantasma" routerLink="/mapa" class="mt-2 w-full">Ver no mapa</a>
        </section>
      </aside>
    </div>
  `,
})
export class HomePage {
  private readonly router = inject(Router);
  private readonly ubsService = inject(UbsService);
  private readonly medicamentoService = inject(MedicamentoService);
  private readonly preferencias = inject(PreferenciasService);

  protected termo = '';
  protected readonly posicao = signal<Coordenadas | null>(null);

  private readonly unidades = toSignal(this.ubsService.listar().pipe(catchError(() => of([]))), {
    initialValue: [] as Ubs[],
  });

  protected readonly categorias = toSignal(
    this.medicamentoService.categorias().pipe(catchError(() => of([]))),
    { initialValue: [] as string[] },
  );

  protected readonly ubsReferencia = computed(
    () => this.unidades().find((u) => u.id === this.preferencias.ubsReferenciaId()) ?? null,
  );

  protected readonly proximas = computed(() => {
    const pos = this.posicao();
    const lista = this.unidades().map((ubs) => ({
      ubs,
      distancia: pos ? distanciaKm(pos, ubs) : null,
    }));
    if (pos) lista.sort((a, b) => a.distancia! - b.distancia!);
    return lista.slice(0, 4);
  });

  private readonly chaveContinuos = computed(() => ({
    ids: this.preferencias.remediosContinuos(),
    ubsId: this.preferencias.ubsReferenciaId(),
  }));

  protected readonly continuos = toSignal(
    toObservable(this.chaveContinuos).pipe(
      switchMap(({ ids, ubsId }) =>
        ids.length === 0
          ? of([])
          : forkJoin(
              ids.map((id) =>
                forkJoin({
                  medicamento: this.medicamentoService.buscarPorId(id),
                  disponibilidade: this.medicamentoService.disponibilidade(id),
                }).pipe(
                  map(({ medicamento, disponibilidade }): RemedioContinuo => {
                    const naReferencia = disponibilidade.find((d) => d.ubs.id === ubsId);
                    const melhor = ORDEM_STATUS.find((s) =>
                      disponibilidade.some((d) => d.status === s),
                    );
                    return {
                      medicamento,
                      status: naReferencia?.status ?? melhor ?? 'EM_FALTA',
                    };
                  }),
                  catchError(() => of(null)),
                ),
              ),
            ).pipe(map((r) => r.filter((x): x is RemedioContinuo => x !== null))),
      ),
    ),
    { initialValue: [] as RemedioContinuo[] },
  );

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  protected buscar(): void {
    this.router.navigate(['/medicamentos'], { queryParams: { busca: this.termo || null } });
  }
}
