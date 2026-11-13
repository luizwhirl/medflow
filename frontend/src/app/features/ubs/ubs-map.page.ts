import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import {
  Coordenadas,
  distanciaKm,
  formatarDistancia,
  linkRota,
  posicaoAtual,
} from '../../core/geo';
import { Disponibilidade } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { STATUS_LABEL, StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';
import { Chip } from '../../shared/components/chip';
import { Mapa, PinoMapa } from '../../shared/components/mapa';

export const RAIOS_KM = [1, 3, 6, 10] as const;

/** Tons dos pinos/círculos (mesmos valores dos tokens de status e da marca). */
const COR = {
  completo: '#0f766e',
  parcial: '#b45309',
  nenhum: '#b91c1c',
  semDado: '#94a3b8',
} as const;
type Tom = keyof typeof COR;

const TOM_STATUS: Record<StatusEstoque, Tom> = {
  DISPONIVEL: 'completo',
  RESERVADO: 'parcial',
  EM_FALTA: 'nenhum',
};

const CLASSE_TEXTO: Record<Tom, string> = {
  completo: 'text-brand-600',
  parcial: 'text-status-reservado',
  nenhum: 'text-status-falta',
  semDado: 'text-content-muted',
};

interface LinhaUbs {
  ubs: Ubs;
  numero: number;
  distancia: number | null;
  referencia: boolean;
  /** Registros dos remédios escolhidos nesta UBS. */
  itens: { medicamento: Medicamento; d: Disponibilidade }[];
  disponiveis: number;
  tom: Tom;
  resumo: string;
}

@Component({
  selector: 'app-ubs-map-page',
  imports: [RouterLink, Mapa, Chip, Button],
  template: `
    <div class="flex flex-col md:-mx-8 md:-my-8 md:h-dvh md:flex-row xl:-mx-14">
      <!-- Painel lateral -->
      <section
        class="order-2 min-w-0 md:order-1 md:w-[23rem] md:shrink-0 md:overflow-y-auto md:border-r md:border-outline md:bg-surface md:px-7 md:py-8"
        aria-labelledby="titulo-mapa"
      >
        <h1 id="titulo-mapa" class="text-3xl font-bold tracking-tight">UBS próximas</h1>
        <p class="mt-1 text-content-muted">
          @if (referencia(); as r) {
            Perto de {{ r.bairro }} ·
          }
          {{ linhas().length }} {{ linhas().length === 1 ? 'resultado' : 'resultados' }}
        </p>

        <label class="mt-4 block">
          <span class="mb-1 block text-sm font-semibold">Remédio</span>
          <select
            class="control w-full"
            [value]="escolha()"
            (change)="escolha.set($any($event.target).value)"
          >
            @if (meusIds().length) {
              <option value="meus">Meus remédios ({{ meusIds().length }})</option>
            }
            <option value="">Nenhum (só as unidades)</option>
            @for (m of medicamentos(); track m.id) {
              <option [value]="m.id">{{ m.nome }} {{ m.concentracao }}</option>
            }
          </select>
        </label>

        <div
          class="-mx-4 mt-3 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] md:-mx-7 md:px-7"
          role="group"
          aria-label="Filtros"
        >
          @if (escolhidos().length) {
            <app-chip [(selecionado)]="soComRemedios">{{
              escolha() === 'meus' ? 'Com meus remédios' : 'Com o remédio'
            }}</app-chip>
          }
          @if (posicao()) {
            @for (r of raios; track r) {
              <app-chip
                [selecionado]="raioKm() === r"
                (selecionadoChange)="raioKm.set($event ? r : null)"
                >{{ r }} km</app-chip
              >
            }
          }
        </div>
        @if (!posicao()) {
          <p class="mt-2 text-xs text-content-muted">
            Permita a localização para filtrar por raio e ver as distâncias.
          </p>
        }

        <ul class="mt-4 flex flex-col gap-3" aria-label="Unidades no mapa">
          @for (l of linhas(); track l.ubs.id) {
            @let ativo = selecionado() === l.ubs.id;
            <li>
              <button
                type="button"
                (click)="selecionado.set(l.ubs.id)"
                [attr.aria-pressed]="ativo"
                class="flex w-full items-start gap-4 rounded-card border px-5 py-4 text-left transition-colors"
                [class]="
                  ativo
                    ? 'border-2 border-brand-600 bg-brand-50'
                    : 'border-outline bg-surface hover:border-brand-500'
                "
              >
                <span
                  class="grid size-9 shrink-0 place-items-center rounded-full font-bold text-white"
                  [style.background]="cor[l.tom]"
                  >{{ l.numero }}</span
                >
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-lg font-bold">{{ l.ubs.nome }}</span>
                  <span class="block text-sm text-content-muted">
                    @if (l.distancia !== null) {
                      {{ distancia(l.distancia) }} ·
                    }
                    {{ l.referencia ? 'Sua UBS' : l.ubs.bairro }}
                  </span>
                  @if (l.resumo) {
                    <span class="mt-1 block font-semibold" [class]="texto[l.tom]">{{
                      l.resumo
                    }}</span>
                  }
                </span>
              </button>
            </li>
          } @empty {
            <li class="body-muted">Nenhuma unidade neste filtro.</li>
          }
        </ul>
      </section>

      <!-- Mapa -->
      <div class="relative order-1 mb-4 min-w-0 md:order-2 md:mb-0 md:flex-1">
        <app-mapa
          class="h-80 overflow-hidden rounded-card md:h-full md:rounded-none"
          [pinos]="pinos()"
          [centro]="posicao()"
          [raioKm]="posicao() ? raioKm() : null"
          [selecionado]="selecionado()"
          (selecionar)="selecionado.set($event)"
        />

        @if (atual(); as a) {
          <article
            class="surface-card mt-3 p-5 md:absolute md:inset-x-6 md:bottom-6 md:z-[500] md:mt-0 md:p-7 md:shadow-lg"
            aria-live="polite"
          >
            <div class="flex items-start gap-3">
              <h2 class="min-w-0 flex-1 text-2xl font-bold">{{ a.ubs.nome }}</h2>
              @if (a.resumo) {
                <span
                  class="whitespace-nowrap rounded-full px-3 py-1 font-semibold"
                  [class]="
                    a.tom === 'completo'
                      ? 'bg-status-disponivel-subtle text-status-disponivel'
                      : a.tom === 'parcial'
                        ? 'bg-status-reservado-subtle text-status-reservado'
                        : 'bg-status-falta-subtle text-status-falta'
                  "
                  >{{ a.resumo }}</span
                >
              }
            </div>
            <p class="mt-1 text-content-muted">
              @if (a.distancia !== null) {
                {{ distancia(a.distancia) }} ·
              }
              {{ a.ubs.bairro }}
              @if (a.ubs.horario) {
                · {{ a.ubs.horario }}
              }
            </p>
            @if (a.itens.length) {
              <ul class="mt-4 flex flex-wrap gap-2">
                @for (i of a.itens; track i.medicamento.id) {
                  <li class="rounded-control bg-canvas px-3 py-1.5 text-sm">
                    {{ i.medicamento.nome }} {{ i.medicamento.concentracao }} ·
                    {{
                      i.d.status === 'DISPONIVEL'
                        ? '~' + i.d.quantidade + ' un.'
                        : statusLabel[i.d.status]
                    }}
                  </li>
                }
              </ul>
            }
            <div class="mt-5 grid gap-3 sm:grid-cols-2">
              <a appButton [href]="rota(a.ubs)" target="_blank" rel="noopener">Como chegar</a>
              <a appButton variante="secundario" [routerLink]="['/ubs', a.ubs.id]"
                >Ver estoque completo</a
              >
            </div>
          </article>
        }
      </div>
    </div>
  `,
})
export class UbsMapPage {
  private readonly ubsService = inject(UbsService);
  private readonly medicamentoService = inject(MedicamentoService);
  private readonly preferencias = inject(PreferenciasService);

  protected readonly raios = RAIOS_KM;
  protected readonly cor = COR;
  protected readonly texto = CLASSE_TEXTO;
  protected readonly statusLabel = STATUS_LABEL;
  protected readonly distancia = formatarDistancia;
  protected readonly rota = linkRota;

  protected readonly raioKm = signal<number | null>(6);
  protected readonly soComRemedios = signal(false);
  protected readonly posicao = signal<Coordenadas | null>(null);
  protected readonly meusIds = this.preferencias.remediosContinuos;
  /** 'meus' = remédios de uso contínuo; '' = nenhum; ou o id de um medicamento. */
  protected readonly escolha = linkedSignal(() => (this.meusIds().length ? 'meus' : ''));

  private readonly unidades = toSignal(this.ubsService.listar().pipe(catchError(() => of([]))), {
    initialValue: [] as Ubs[],
  });

  protected readonly medicamentos = toSignal(
    this.medicamentoService.buscar().pipe(catchError(() => of([]))),
    { initialValue: [] as Medicamento[] },
  );

  protected readonly referencia = computed(
    () => this.unidades().find((u) => u.id === this.preferencias.ubsReferenciaId()) ?? null,
  );

  private readonly idsEscolhidos = computed(() => {
    const e = this.escolha();
    return e === 'meus' ? this.meusIds() : e ? [Number(e)] : [];
  });

  /** Remédios escolhidos com a disponibilidade de cada um. */
  protected readonly escolhidos = toSignal(
    toObservable(this.idsEscolhidos).pipe(
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

  protected readonly linhas = computed((): LinhaUbs[] => {
    const pos = this.posicao();
    const raio = this.raioKm();
    const escolhidos = this.escolhidos();
    const refId = this.preferencias.ubsReferenciaId();
    return this.unidades()
      .map((ubs) => {
        const itens = escolhidos.flatMap(({ medicamento, disponibilidade }) => {
          const d = disponibilidade.find((x) => x.ubs.id === ubs.id);
          return d ? [{ medicamento, d }] : [];
        });
        const disponiveis = itens.filter((i) => i.d.status === 'DISPONIVEL').length;
        return {
          ubs,
          distancia: pos ? distanciaKm(pos, ubs) : null,
          referencia: ubs.id === refId,
          itens,
          disponiveis,
          ...this.classificar(escolhidos.length, itens, disponiveis),
        };
      })
      .filter((l) => l.distancia === null || raio === null || l.distancia <= raio)
      .filter((l) => !this.soComRemedios() || l.disponiveis > 0)
      .sort((a, b) =>
        a.distancia !== null && b.distancia !== null
          ? a.distancia - b.distancia
          : a.ubs.nome.localeCompare(b.ubs.nome),
      )
      .map((l, i) => ({ ...l, numero: i + 1 }));
  });

  protected readonly selecionado = linkedSignal<LinhaUbs[], number | null>({
    source: this.linhas,
    computation: (linhas, anterior) =>
      linhas.some((l) => l.ubs.id === anterior?.value)
        ? anterior!.value
        : (linhas[0]?.ubs.id ?? null),
  });

  protected readonly atual = computed(
    () => this.linhas().find((l) => l.ubs.id === this.selecionado()) ?? null,
  );

  protected readonly pinos = computed((): PinoMapa[] =>
    this.linhas().map((l) => ({
      id: l.ubs.id,
      latitude: l.ubs.latitude,
      longitude: l.ubs.longitude,
      cor: COR[l.tom],
      rotulo: l.ubs.nome,
      numero: l.numero,
    })),
  );

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  /** Tom (cor) e texto-resumo da UBS para os remédios escolhidos. */
  private classificar(
    total: number,
    itens: LinhaUbs['itens'],
    disponiveis: number,
  ): { tom: Tom; resumo: string } {
    if (total === 0) return { tom: 'completo', resumo: '' };
    if (total === 1) {
      const d = itens[0]?.d;
      if (!d) return { tom: 'semDado', resumo: 'Sem informação' };
      return {
        tom: TOM_STATUS[d.status],
        resumo: d.status === 'DISPONIVEL' ? `~${d.quantidade} un.` : STATUS_LABEL[d.status],
      };
    }
    const resumo = `${disponiveis} de ${total} remédios`;
    if (disponiveis === total) return { tom: 'completo', resumo };
    if (disponiveis === 0) return { tom: 'nenhum', resumo };
    return { tom: 'parcial', resumo };
  }
}
