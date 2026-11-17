import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import {
  Coordenadas,
  distanciaKm,
  formatarDistancia,
  linkRota,
  posicaoAtual,
} from '../../core/geo';
import { Disponibilidade, statusExibido, temEstoque } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';
import { Chip } from '../../shared/components/chip';
import { MapaIlustrado } from '../../shared/components/mapa-ilustrado';
import { SearchField } from '../../shared/components/search-field';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

interface Remedio {
  medicamento: Medicamento;
  disponibilidade: Disponibilidade[];
  /** Status na UBS de referência; `null` sem UBS escolhida ou sem registro do item nela. */
  status: StatusEstoque | null;
  /** Registro na UBS de referência, se houver. */
  naReferencia: Disponibilidade | null;
  /** Unidade mais próxima com o item disponível (fora a de referência). */
  ondeTem: { ubs: Ubs; distancia: number | null } | null;
}

/** Ícone de pílula (traço, viewBox 24). */
const ICONE_PILULA = 'M10.5 20.5 3.5 13.5a4.95 4.95 0 0 1 7-7l7 7a4.95 4.95 0 0 1-7 7zM7 10l7 7';

@Component({
  selector: 'app-home-page',
  imports: [
    RouterLink,
    NgTemplateOutlet,
    SearchField,
    Button,
    Chip,
    StatusBadge,
    SyncIndicator,
    MapaIlustrado,
  ],
  template: `
    <ng-template #pilula>
      <span
        class="grid size-12 shrink-0 place-items-center rounded-control bg-brand-50 text-brand-600"
      >
        <svg
          class="size-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path [attr.d]="iconePilula" />
        </svg>
      </span>
    </ng-template>

    <header class="flex items-start gap-4">
      <div class="min-w-0 flex-1">
        <p class="text-content-muted">Olá!</p>
        <h1 class="mt-1 text-3xl font-bold tracking-tight xl:text-4xl">
          Qual remédio você procura?
        </h1>
      </div>
      <a
        routerLink="/avisos"
        class="hidden size-12 place-items-center rounded-control border border-outline bg-surface text-content xl:grid"
        aria-label="Avisos"
      >
        <svg
          class="size-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0" />
        </svg>
      </a>
    </header>

    <!-- Busca: em cartão no desktop, solta no tablet/celular -->
    <section
      class="mt-6 xl:rounded-card xl:border xl:border-outline xl:bg-surface xl:p-7"
      aria-label="Busca"
    >
      <div class="flex flex-col gap-3 xl:flex-row xl:items-end">
        <app-search-field
          class="min-w-0 flex-1"
          rotulo="Nome do medicamento"
          [rotuloVisivel]="true"
          placeholder="Ex.: Metformina, Losartana, Insulina"
          [(valor)]="termo"
          (buscar)="buscar(termo)"
        />
        <div class="hidden gap-3 xl:flex">
          @if (ubsReferencia(); as u) {
            <a
              appButton
              variante="secundario"
              routerLink="/mapa"
              [attr.aria-label]="'Perto de ' + u.bairro + ', trocar'"
            >
              <svg
                class="size-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path [attr.d]="iconePino" />
              </svg>
              {{ u.bairro }}
            </a>
          }
          <button appButton type="button" (click)="buscar(termo)">Buscar</button>
        </div>
      </div>

      <div class="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm xl:hidden">
        @if (ubsReferencia(); as u) {
          <span class="text-content-muted"
            >Perto de <strong class="text-content">{{ u.bairro }}</strong> ·
            <a routerLink="/mapa" class="text-brand-600">alterar</a></span
          >
          <app-sync-indicator [em]="u.ultimaSincronizacao" />
        }
      </div>

      @if (buscasRecentes().length || categorias().length) {
        <div class="mt-4 flex flex-wrap items-center gap-2">
          @if (buscasRecentes().length) {
            <span class="text-sm text-content-muted">Buscas recentes:</span>
            @for (b of buscasRecentes(); track b) {
              <app-chip variante="sugestao" (selecionadoChange)="buscar(b)">{{ b }}</app-chip>
            }
          }
        </div>
        @if (categorias().length) {
          <div
            class="mt-2 flex flex-wrap items-center gap-2"
            role="group"
            aria-labelledby="titulo-atalhos"
          >
            <span id="titulo-atalhos" class="text-sm text-content-muted">Por condição:</span>
            @for (c of categorias(); track c) {
              <app-chip variante="sugestao" (selecionadoChange)="porCategoria(c)">{{ c }}</app-chip>
            }
          </div>
        }
      }
    </section>

    <div class="mt-8 grid gap-8 xl:grid-cols-[minmax(0,1fr)_23rem]">
      <div class="flex min-w-0 flex-col gap-8">
        <section aria-labelledby="titulo-continuos">
          <div class="mb-3 flex items-baseline justify-between gap-2">
            <h2 id="titulo-continuos" class="text-xl font-bold">Meus remédios de uso contínuo</h2>
            <a routerLink="/lista" class="font-semibold text-brand-600">
              <span class="hidden xl:inline">Ver lista completa</span>
              <span class="xl:hidden">Ver lista</span>
            </a>
          </div>

          <!-- Desktop: tabela -->
          @if (remedios().length) {
            <div class="surface-card hidden overflow-hidden xl:block">
              <table class="w-full text-left">
                <thead
                  class="border-b border-outline text-xs font-semibold uppercase tracking-wider text-content-muted"
                >
                  <tr>
                    <th class="px-7 py-4" colspan="2">Medicamento</th>
                    <th class="px-3 py-4">Na sua UBS</th>
                    <th class="px-3 py-4">Previsão / estoque</th>
                    <th class="px-3 py-4">Onde tem agora</th>
                    <th class="px-7 py-4"><span class="sr-only">Ação</span></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-outline">
                  @for (r of remedios(); track r.medicamento.id) {
                    <tr>
                      <td class="w-0 py-5 pl-7 pr-4">
                        <ng-container *ngTemplateOutlet="pilula" />
                      </td>
                      <td class="py-5 pr-3 font-semibold">
                        {{ r.medicamento.nome }} {{ r.medicamento.concentracao }}
                      </td>
                      <td class="px-3 py-5">
                        @if (r.status) {
                          <app-status-badge [status]="r.status" />
                        } @else {
                          <span class="text-sm text-content-muted">{{ semStatus() }}</span>
                        }
                      </td>
                      <td class="px-3 py-5 text-sm text-content-muted">{{ previsao(r) }}</td>
                      <td class="px-3 py-5 text-sm font-semibold text-brand-600">
                        {{ ondeTem(r) }}
                      </td>
                      <td class="py-5 pl-3 pr-7 text-right">
                        <a
                          appButton
                          variante="secundario"
                          class="h-11! whitespace-nowrap"
                          [routerLink]="acao(r).link"
                          >{{ acao(r).rotulo }}</a
                        >
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              <a
                routerLink="/medicamentos"
                class="block border-t border-outline px-7 py-4 font-semibold text-brand-600 hover:bg-canvas"
                >+ Adicionar remédio</a
              >
            </div>
          }

          <!-- Tablet e celular: cartões -->
          <div class="grid gap-4 sm:grid-cols-2" [class]="remedios().length ? 'xl:hidden' : ''">
            @for (r of remedios(); track r.medicamento.id) {
              <a
                [routerLink]="acao(r).link"
                class="surface-card block p-6 transition-shadow hover:ring-2 hover:ring-brand-100 xl:hidden"
              >
                <div class="flex items-start justify-between gap-2">
                  <ng-container *ngTemplateOutlet="pilula" />
                  @if (r.status) {
                    <app-status-badge [status]="r.status" tamanho="md" />
                  } @else {
                    <span class="text-sm text-content-muted">{{ semStatus() }}</span>
                  }
                </div>
                <h3 class="mt-4 text-lg font-bold">
                  {{ r.medicamento.nome }} {{ r.medicamento.concentracao }}
                </h3>
                <p class="mt-1 text-content-muted">{{ previsao(r) }}</p>
                <p class="mt-2 font-semibold text-brand-600">{{ ondeTem(r) }}</p>
              </a>
            }
            <a
              routerLink="/medicamentos"
              class="grid min-h-40 place-items-center rounded-card border-2 border-dashed border-outline p-6 text-lg font-semibold text-content-muted hover:border-brand-500 hover:text-brand-600"
            >
              + Adicionar remédio
            </a>
          </div>
        </section>

        <section aria-labelledby="titulo-proximas">
          <div class="mb-3 flex items-baseline justify-between gap-2">
            <h2 id="titulo-proximas" class="text-xl font-bold">
              {{ posicao() ? 'UBS próximas' : 'Unidades de saúde'
              }}{{ remedios().length ? ' com seus remédios' : '' }}
            </h2>
            <a routerLink="/mapa" class="font-semibold text-brand-600">Abrir mapa</a>
          </div>
          <div class="grid gap-4 sm:grid-cols-3">
            @for (p of proximas(); track p.ubs.id) {
              <a
                [routerLink]="['/ubs', p.ubs.id]"
                class="surface-card block p-6 transition-shadow hover:ring-2 hover:ring-brand-100"
              >
                @if (p.distancia !== null) {
                  <p class="text-sm text-content-muted">{{ distancia(p.distancia) }}</p>
                }
                <h3 class="mt-1 truncate text-lg font-bold">{{ p.ubs.nome }}</h3>
                @if (remedios().length) {
                  <div
                    class="mt-3 h-2 overflow-hidden rounded-full bg-outline"
                    role="progressbar"
                    [attr.aria-valuenow]="p.disponiveis"
                    aria-valuemin="0"
                    [attr.aria-valuemax]="remedios().length"
                  >
                    <div
                      class="h-full rounded-full bg-brand-600"
                      [style.width.%]="(100 * p.disponiveis) / remedios().length"
                    ></div>
                  </div>
                  <p class="mt-3 text-sm text-content-muted">
                    {{ p.disponiveis }} de {{ remedios().length }} remédios disponíveis
                  </p>
                } @else {
                  <p class="mt-1 text-sm text-content-muted">{{ p.ubs.bairro }}</p>
                }
              </a>
            } @empty {
              <p class="body-muted">Nenhuma unidade encontrada.</p>
            }
          </div>
        </section>
      </div>

      <aside class="grid content-start gap-8 sm:grid-cols-2 xl:grid-cols-1">
        <section aria-labelledby="titulo-referencia" class="surface-card overflow-hidden">
          <app-mapa-ilustrado class="h-36 xl:h-44" />
          <div class="p-7">
            <div class="flex items-baseline justify-between gap-2">
              <h2
                id="titulo-referencia"
                class="text-xs font-semibold uppercase tracking-wider text-content-muted"
              >
                Minha UBS de referência
              </h2>
              @if (ubsReferencia()) {
                <a routerLink="/mapa" class="font-semibold text-brand-600">Trocar</a>
              }
            </div>
            @if (ubsReferencia(); as u) {
              <p class="mt-3 text-2xl font-bold">{{ u.nome }}</p>
              <p class="mt-1 text-content-muted">
                @if (distanciaReferencia() !== null) {
                  ~{{ distancia(distanciaReferencia()!) }} de você ·
                }
                {{ u.bairro }}
              </p>
              <div class="mt-5 flex gap-3">
                <a appButton class="flex-1" [routerLink]="['/ubs', u.id]">Ver estoque</a>
                <a
                  appButton
                  variante="secundario"
                  class="flex-1 max-xl:hidden!"
                  [href]="rota(u)"
                  target="_blank"
                  rel="noopener"
                  >Como chegar</a
                >
              </div>
            } @else {
              <p class="mt-3 text-content-muted">Você ainda não escolheu sua UBS.</p>
              <a appButton routerLink="/mapa" class="mt-5 w-full">Escolher UBS</a>
            }
          </div>
        </section>

        <section aria-labelledby="titulo-avisos" class="surface-card p-7">
          <div class="flex items-baseline justify-between gap-2 border-b border-outline pb-3">
            <h2 id="titulo-avisos" class="text-xl font-bold">Avisos recentes</h2>
            <a routerLink="/avisos" class="hidden font-semibold text-brand-600 xl:inline"
              >Ver todos</a
            >
          </div>
          <p class="mt-4 text-content-muted">
            Você ainda não tem avisos. Quando um remédio da sua lista chegar, ele aparece aqui.
          </p>
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

  protected readonly iconePilula = ICONE_PILULA;
  protected readonly iconePino =
    'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12zM12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z';
  protected readonly distancia = formatarDistancia;
  protected readonly rota = linkRota;
  protected readonly buscasRecentes = this.preferencias.buscasRecentes;

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

  protected readonly distanciaReferencia = computed(() => {
    const pos = this.posicao();
    const u = this.ubsReferencia();
    return pos && u ? distanciaKm(pos, u) : null;
  });

  private readonly carregados = toSignal(
    toObservable(computed(() => this.preferencias.remediosContinuos())).pipe(
      switchMap((ids) =>
        ids.length === 0
          ? of([])
          : forkJoin(
              ids.map((id) =>
                forkJoin({
                  medicamento: this.medicamentoService.buscarPorId(id),
                  disponibilidade: this.medicamentoService.disponibilidade(id),
                }).pipe(catchError(() => of(null))),
              ),
            ).pipe(map((r) => r.filter((x) => x !== null))),
      ),
    ),
    { initialValue: [] as { medicamento: Medicamento; disponibilidade: Disponibilidade[] }[] },
  );

  protected readonly remedios = computed((): Remedio[] => {
    const refId = this.preferencias.ubsReferenciaId();
    const pos = this.posicao();
    return this.carregados().map(({ medicamento, disponibilidade }) => {
      const naReferencia = disponibilidade.find((d) => d.ubs.id === refId) ?? null;
      const candidatas = disponibilidade
        .filter((d) => temEstoque(d) && d.ubs.id !== refId)
        .map((d) => ({ ubs: d.ubs, distancia: pos ? distanciaKm(pos, d.ubs) : null }))
        .sort((a, b) => (a.distancia ?? 0) - (b.distancia ?? 0));
      return {
        medicamento,
        disponibilidade,
        status: naReferencia ? statusExibido(naReferencia) : null,
        naReferencia,
        ondeTem: candidatas[0] ?? null,
      };
    });
  });

  protected readonly proximas = computed(() => {
    const pos = this.posicao();
    const remedios = this.remedios();
    const lista = this.unidades().map((ubs) => ({
      ubs,
      distancia: pos ? distanciaKm(pos, ubs) : null,
      disponiveis: remedios.filter((r) =>
        r.disponibilidade.some((d) => d.ubs.id === ubs.id && temEstoque(d)),
      ).length,
    }));
    if (pos) lista.sort((a, b) => a.distancia! - b.distancia!);
    return lista.slice(0, 3);
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  protected previsao(r: Remedio): string {
    if (r.naReferencia && temEstoque(r.naReferencia))
      return `~${r.naReferencia.quantidade} un. em estoque`;
    if (r.naReferencia?.status === 'RESERVADO')
      return `${r.naReferencia.quantidade} un. reservadas`;
    return 'Sem previsão de remessa';
  }

  /** Texto da coluna "Na sua UBS" quando não há status para mostrar. */
  protected semStatus(): string {
    return this.ubsReferencia() ? 'Sem registro na sua UBS' : 'Escolha sua UBS';
  }

  protected ondeTem(r: Remedio): string {
    if (r.naReferencia && temEstoque(r.naReferencia)) return 'Na sua UBS';
    if (!r.ondeTem) return 'Nenhuma UBS com estoque';
    const d = r.ondeTem.distancia;
    return `${r.ondeTem.ubs.nome}${d !== null ? ' · ' + formatarDistancia(d) : ''}`;
  }

  protected acao(r: Remedio): { rotulo: string; link: unknown[] } {
    return r.naReferencia && temEstoque(r.naReferencia)
      ? { rotulo: 'Ver estoque', link: ['/ubs', r.naReferencia.ubs.id] }
      : { rotulo: 'Rastrear', link: ['/medicamentos', r.medicamento.id] };
  }

  protected buscar(termo: string): void {
    this.preferencias.registrarBusca(termo);
    this.router.navigate(['/medicamentos'], { queryParams: { busca: termo.trim() || null } });
  }

  protected porCategoria(categoria: string): void {
    this.router.navigate(['/medicamentos'], { queryParams: { categoria } });
  }
}
