import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import {
  Coordenadas,
  distanciaKm,
  formatarDistancia,
  linkRota,
  posicaoAtual,
} from '../../core/geo';
import { Disponibilidade, statusExibido, temEstoque } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { Button } from '../../shared/components/button';
import { MapaIlustrado } from '../../shared/components/mapa-ilustrado';
import { EtapaTimeline, Timeline } from '../../shared/components/timeline';

type Estado =
  | { carregando: true }
  | { carregando: false; medicamento: Medicamento | null; disponibilidade: Disponibilidade[] };

export const ETAPAS_REMESSA: readonly EtapaTimeline[] = [
  { titulo: 'Pedido aprovado pela UBS' },
  { titulo: 'Separado na Central de Abastecimento' },
  { titulo: 'Em trânsito' },
  { titulo: 'Disponível na sua UBS' },
];

const ORDEM = { DISPONIVEL: 0, RESERVADO: 1, EM_FALTA: 2 } as const;

/** Detalhe do medicamento (Figma: "Desktop · Buscar e rastrear", painel da direita). */
@Component({
  selector: 'app-rastreio-medicamento',
  imports: [RouterLink, Button, Timeline, MapaIlustrado],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando…</p>
    } @else if (!e.medicamento) {
      <p class="feedback-error">Medicamento não encontrado.</p>
    } @else {
      <nav class="text-sm text-content-muted" aria-label="Trilha">
        <a routerLink="/medicamentos" class="hover:underline">Buscar</a>
        <span aria-hidden="true"> › </span>
        <span>{{ e.medicamento.nome }} {{ e.medicamento.concentracao }}</span>
      </nav>

      <header class="mt-5 flex flex-wrap items-start gap-4">
        <span
          class="grid size-14 shrink-0 place-items-center rounded-card bg-brand-50 text-brand-600"
        >
          <svg
            class="size-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            aria-hidden="true"
          >
            <path d="M10.5 20.5 3.5 13.5a4.95 4.95 0 0 1 7-7l7 7a4.95 4.95 0 0 1-7 7zM7 10l7 7" />
          </svg>
        </span>
        <div class="min-w-0 flex-1 basis-64">
          <h1 class="text-3xl font-bold tracking-tight">
            {{ e.medicamento.nome }} {{ e.medicamento.concentracao }}
          </h1>
          <p class="mt-1 text-content-muted">
            <span class="capitalize">{{ e.medicamento.formaFarmaceutica }}</span>
            @if (e.medicamento.rename) {
              · RENAME
            }
            · CATMAT {{ e.medicamento.catmat }}
          </p>
        </div>
        <div class="flex flex-wrap gap-3 whitespace-nowrap">
          <button
            appButton
            type="button"
            variante="secundario"
            [attr.aria-pressed]="naLista()"
            (click)="preferencias.alternarRemedioContinuo(id())"
          >
            <svg
              class="size-5"
              viewBox="0 0 24 24"
              [attr.fill]="naLista() ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="2"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M6 3h12v18l-6-4-6 4z" />
            </svg>
            {{ naLista() ? 'Na minha lista' : 'Adicionar à lista' }}
          </button>
          <button appButton type="button" disabled aria-describedby="aviso-em-breve">
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
            Avisar quando chegar
          </button>
          <p id="aviso-em-breve" class="w-full text-right text-xs text-content-muted">
            Os avisos chegam em breve.
          </p>
        </div>
      </header>

      @if (alerta(); as a) {
        <div
          class="mt-6 rounded-card bg-status-falta-subtle px-7 py-5 text-status-falta"
          role="status"
        >
          <p class="font-bold">Em falta na {{ a.ubs }}</p>
          <p class="mt-1">
            @if (a.alternativa) {
              Enquanto isso, a {{ a.alternativa }} tem estoque.
            } @else {
              Nenhuma unidade tem estoque agora.
            }
          </p>
        </div>
      }

      <div class="mt-6 grid gap-6 xl:grid-cols-2">
        <section class="surface-card self-start p-7" aria-labelledby="titulo-remessa">
          <h2 id="titulo-remessa" class="mb-5 flex items-center gap-2 text-xl font-bold">
            <svg
              class="size-6 text-brand-600"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path
                d="M3 6h11v9H3zM14 9h4l3 3v3h-7M7.5 18.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM17.5 18.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z"
              />
            </svg>
            Rastreamento da remessa
          </h2>
          <app-timeline [etapas]="etapas" [atual]="etapaAtual()" />
          @if (etapaAtual() === null) {
            <p class="mt-4 text-sm text-content-muted">
              A BNAFAR ainda não informa as etapas da remessa; as datas aparecem quando esse dado
              estiver disponível.
            </p>
          }
        </section>

        <section class="surface-card self-start overflow-hidden" aria-labelledby="titulo-onde">
          <app-mapa-ilustrado class="h-40" />
          <div class="p-7 pt-5">
            <h2 id="titulo-onde" class="border-b border-outline pb-2 text-xl font-bold">
              Onde tem agora
            </h2>
            <ul class="divide-y divide-outline">
              @for (u of unidades(); track u.d.ubs.id) {
                <li class="flex items-center gap-3 py-3">
                  <a [routerLink]="['/ubs', u.d.ubs.id]" class="min-w-0 flex-1">
                    <span class="block truncate font-semibold">{{ u.d.ubs.nome }}</span>
                    <span class="block text-sm text-content-muted">
                      @if (u.distancia !== null) {
                        {{ distancia(u.distancia) }} ·
                      }
                      {{ u.referencia ? 'Sua UBS' : u.d.ubs.bairro }}
                    </span>
                  </a>
                  <span
                    class="whitespace-nowrap rounded-full px-3 py-1 text-sm font-semibold"
                    [class]="
                      u.status === 'DISPONIVEL'
                        ? 'bg-status-disponivel-subtle text-status-disponivel'
                        : u.status === 'RESERVADO'
                          ? 'bg-status-reservado-subtle text-status-reservado'
                          : 'bg-status-falta-subtle text-status-falta'
                    "
                    >{{
                      u.status === 'EM_FALTA'
                        ? 'Em falta'
                        : u.status === 'RESERVADO'
                          ? 'Reservado'
                          : '~' + u.d.quantidade + ' un.'
                    }}</span
                  >
                  <a
                    [href]="rota(u.d.ubs)"
                    target="_blank"
                    rel="noopener"
                    class="font-semibold text-brand-600"
                    >Rota</a
                  >
                </li>
              } @empty {
                <li class="py-3 body-muted">Nenhuma unidade informou estoque deste item.</li>
              }
            </ul>
          </div>
        </section>
      </div>
    }
  `,
})
export class RastreioMedicamento {
  private readonly medicamentoService = inject(MedicamentoService);
  protected readonly preferencias = inject(PreferenciasService);

  readonly id = input.required<number>();
  protected readonly etapas = ETAPAS_REMESSA;
  protected readonly distancia = formatarDistancia;
  protected readonly rota = linkRota;
  private readonly posicao = signal<Coordenadas | null>(null);

  protected readonly naLista = computed(() =>
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

  /** Unidades com o item: disponíveis primeiro, depois pela distância. */
  protected readonly unidades = computed(() => {
    const e = this.estado();
    if (e.carregando) return [];
    const pos = this.posicao();
    const ref = this.preferencias.ubsReferenciaId();
    return e.disponibilidade
      .map((d) => ({
        d,
        status: statusExibido(d),
        distancia: pos ? distanciaKm(pos, d.ubs) : null,
        referencia: d.ubs.id === ref,
      }))
      .sort((a, b) => ORDEM[a.status] - ORDEM[b.status] || (a.distancia ?? 0) - (b.distancia ?? 0));
  });

  /** Alerta quando o item está em falta na UBS de referência. */
  protected readonly alerta = computed(() => {
    const lista = this.unidades();
    const ref = lista.find((u) => u.referencia);
    if (!ref || ref.status !== 'EM_FALTA') return null;
    const alt = lista.find((u) => temEstoque(u.d));
    return {
      ubs: ref.d.ubs.nome,
      alternativa: alt
        ? `${alt.d.ubs.nome}${alt.distancia !== null ? ` (${formatarDistancia(alt.distancia)})` : ''}`
        : null,
    };
  });

  /**
   * A API só informa o estoque nas unidades, não a etapa da remessa.
   * Só marcamos a remessa como concluída quando a UBS de referência tem o item em estoque;
   * sem UBS de referência ou sem registro nela, a etapa fica desconhecida.
   */
  protected readonly etapaAtual = computed(() => {
    const ref = this.unidades().find((u) => u.referencia);
    return ref && temEstoque(ref.d) ? ETAPAS_REMESSA.length - 1 : null;
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }
}
