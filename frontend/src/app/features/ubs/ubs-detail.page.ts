import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, interval, map, of, scan, startWith, switchMap } from 'rxjs';

import { linkRota } from '../../core/geo';
import { ItemEstoque, statusExibido } from '../../core/models/estoque.model';
import { STATUS_LABEL, StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import {
  ATUALIZAR_DADOS_A_CADA_MS,
  minutosDesde,
  nivelSincronizacao,
  tempoDesde,
} from '../../core/sincronizacao';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';
import { Chip } from '../../shared/components/chip';
import { Esqueleto } from '../../shared/components/esqueleto';
import { EstadoVazio, ICONES_VAZIO } from '../../shared/components/estado-vazio';
import { Funcionamento } from '../../shared/components/funcionamento';
import { MapaIlustrado } from '../../shared/components/mapa-ilustrado';
import { MensagemErro } from '../../shared/components/mensagem-erro';
import { StatusBadge } from '../../shared/components/status-badge';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'nao-encontrada' }
  | { tipo: 'erro' }
  | { tipo: 'ok'; ubs: Ubs; estoque: ItemEstoque[] };

const ORDEM_STATUS: readonly StatusEstoque[] = ['DISPONIVEL', 'RESERVADO', 'EM_FALTA'];

/** Paths SVG (viewBox 24x24, traço), no mesmo estilo dos ícones do menu. */
const ICONE = {
  local:
    'M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  relogio: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  telefone:
    'M21 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 3.1 4.2 2 2 0 0 1 5.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L9.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z',
  rota: 'M3 11 21 3l-8 18-2-8z',
  estrela: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z',
  check: 'M5 12.5 9.5 17 19 7',
  alerta: 'M12 3 2 20h20zM12 10v4M12 17h.01',
} as const;

@Component({
  selector: 'app-ubs-detail-page',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    Button,
    Chip,
    Esqueleto,
    EstadoVazio,
    Funcionamento,
    MapaIlustrado,
    MensagemErro,
    StatusBadge,
    SyncIndicator,
  ],
  template: `
    <ng-template #icone let-d>
      <svg
        class="size-5 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path [attr.d]="d" />
      </svg>
    </ng-template>

    @let e = estado();
    @switch (e.tipo) {
      @case ('carregando') {
        <app-esqueleto formato="detalhe" [quantidade]="2" rotulo="Carregando a unidade" />
      }
      @case ('nao-encontrada') {
        <app-estado-vazio
          titulo="Unidade não encontrada"
          texto="Ela pode ter saído da lista de unidades. Procure outra no mapa."
          [icone]="iconesVazio.local"
        >
          <a appButton routerLink="/mapa">Abrir mapa</a>
        </app-estado-vazio>
      }
      @case ('erro') {
        <app-mensagem-erro
          titulo="Não foi possível carregar a unidade"
          (tentarDeNovo)="recarregar()"
        />
      }
      @case ('ok') {
        @let u = e.ubs;
        <div
          class="flex flex-col gap-6 md:grid md:grid-cols-[22rem_minmax(0,1fr)] md:items-start xl:grid-cols-[26rem_minmax(0,1fr)] xl:gap-8"
        >
          <!-- Dados da unidade -->
          <section
            aria-labelledby="titulo-ubs"
            class="surface-card overflow-hidden md:sticky md:top-8"
          >
            <app-mapa-ilustrado class="h-28 sm:h-36" />
            <div class="p-5 sm:p-7">
              <ul class="flex flex-wrap gap-2 text-xs font-semibold" aria-label="Identificação">
                <li class="rounded-full bg-canvas px-2.5 py-1 text-content-muted">{{ u.tipo }}</li>
                <li class="rounded-full bg-canvas px-2.5 py-1 text-content-muted">
                  CNES {{ u.cnes }}
                </li>
                @if (referencia()) {
                  <li class="rounded-full bg-brand-50 px-2.5 py-1 text-brand-700">
                    Sua UBS de referência
                  </li>
                }
              </ul>
              <h1 id="titulo-ubs" class="mt-3 text-2xl font-bold tracking-tight">{{ u.nome }}</h1>

              <dl class="mt-4 flex flex-col gap-3 text-content">
                <div class="flex gap-3">
                  <dt class="text-content-muted">
                    <span class="sr-only">Endereço</span>
                    <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.local }" />
                  </dt>
                  <dd>
                    {{ u.endereco }} · {{ u.bairro }}
                    @if (u.localizacaoAproximada) {
                      <span
                        class="mt-1 block text-sm text-status-alerta"
                        data-teste="local-aproximado"
                      >
                        Localização no mapa aproximada (centro do bairro). Confira o endereço.
                      </span>
                    }
                  </dd>
                </div>
                <div class="flex gap-3">
                  <dt class="text-content-muted">
                    <span class="sr-only">Horário</span>
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones.relogio }"
                    />
                  </dt>
                  <dd>
                    {{ u.horario ?? 'Horário não informado' }}
                    <app-funcionamento class="block" [ubs]="u" />
                  </dd>
                </div>
                <div class="flex gap-3">
                  <dt class="text-content-muted">
                    <span class="sr-only">Telefone</span>
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones.telefone }"
                    />
                  </dt>
                  <dd>{{ u.telefone ?? 'Telefone não informado' }}</dd>
                </div>
              </dl>

              @let s = sincronizacao();
              @if (s.nivel === 'critica') {
                <div
                  class="mt-5 flex gap-3 rounded-control bg-status-falta-subtle p-4 text-sm text-status-falta"
                  data-aviso="sincronizacao"
                >
                  <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.alerta }" />
                  <p>
                    <b>{{ s.titulo }}</b> O estoque abaixo pode estar desatualizado.
                    {{ u.telefone ? 'Ligue antes de ir.' : 'Confirme na unidade antes de ir.' }}
                  </p>
                </div>
              } @else if (s.nivel === 'atrasada') {
                <div
                  class="mt-5 flex gap-3 rounded-control bg-status-alerta-subtle p-4 text-sm text-status-alerta"
                  data-aviso="sincronizacao"
                >
                  <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.relogio }" />
                  <p>{{ s.titulo }} O estoque pode ter mudado desde então.</p>
                </div>
              } @else {
                <app-sync-indicator class="mt-5 block" [em]="u.ultimaSincronizacao" />
              }

              <div class="mt-5 grid grid-cols-3 gap-2 sm:gap-3">
                <a appButton class="px-2!" [href]="rota(u)" target="_blank" rel="noopener">
                  <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.rota }" />
                  Rota
                </a>
                @if (telefoneLink(); as tel) {
                  <a appButton variante="secundario" class="px-2!" [href]="tel">
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones.telefone }"
                    />
                    Ligar
                  </a>
                } @else {
                  <button appButton variante="secundario" class="px-2!" type="button" disabled>
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones.telefone }"
                    />
                    Ligar
                  </button>
                }
                <button
                  appButton
                  type="button"
                  [variante]="referencia() ? 'fantasma' : 'secundario'"
                  class="px-2!"
                  [class.border]="referencia()"
                  [class.border-brand-600]="referencia()"
                  [class.bg-brand-50]="referencia()"
                  [attr.aria-pressed]="referencia()"
                  [attr.aria-label]="
                    referencia() ? 'Desafixar como minha UBS' : 'Fixar como minha UBS'
                  "
                  (click)="alternarReferencia()"
                >
                  <ng-container
                    *ngTemplateOutlet="
                      icone;
                      context: { $implicit: referencia() ? icones.check : icones.estrela }
                    "
                  />
                  {{ referencia() ? 'Minha' : 'Fixar' }}
                </button>
              </div>
            </div>
          </section>

          <!-- Estoque -->
          <section aria-labelledby="titulo-estoque" class="min-w-0">
            <div class="flex items-baseline justify-between gap-3">
              <h2 id="titulo-estoque" class="text-xl font-bold">Estoque da farmácia</h2>
              <span class="text-sm text-content-muted">
                {{ e.estoque.length }} {{ e.estoque.length === 1 ? 'item' : 'itens' }}
              </span>
            </div>

            <div
              class="-mx-4 mt-3 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] sm:mx-0 sm:flex-wrap sm:px-0"
              role="group"
              aria-label="Filtrar por situação"
            >
              <app-chip
                [selecionado]="filtro() === null"
                [fixo]="true"
                (selecionadoChange)="filtro.set(null)"
                >Todos · {{ e.estoque.length }}</app-chip
              >
              @for (st of status; track st) {
                <app-chip
                  [selecionado]="filtro() === st"
                  (selecionadoChange)="filtro.set($event ? st : null)"
                  >{{ rotulo[st] }} · {{ contagem()[st] }}</app-chip
                >
              }
            </div>

            <ul class="surface-list mt-4" aria-label="Itens do estoque">
              @for (l of linhas(); track l.item.medicamento.id) {
                <li>
                  <a
                    [routerLink]="['/medicamentos', l.item.medicamento.id]"
                    class="flex items-center gap-3 px-4 py-3.5 transition-colors hover:bg-surface-muted sm:px-5"
                  >
                    <span class="min-w-0 flex-1">
                      <span class="block font-semibold">
                        {{ l.item.medicamento.nome }} {{ l.item.medicamento.concentracao }}
                      </span>
                      <span class="block text-sm text-content-muted">
                        {{ l.item.medicamento.categoria }} · {{ l.detalhe }}
                      </span>
                    </span>
                    <app-status-badge [status]="l.status" />
                  </a>
                </li>
              } @empty {
                <li>
                  @if (e.estoque.length === 0) {
                    <app-estado-vazio
                      [compacto]="true"
                      titulo="Sem itens de estoque registrados"
                      texto="Esta unidade ainda não informou o estoque da farmácia."
                      [icone]="iconesVazio.caixa"
                    />
                  } @else {
                    <app-estado-vazio
                      [compacto]="true"
                      titulo="Nenhum medicamento com essa situação"
                      [icone]="iconesVazio.caixa"
                    >
                      <button
                        appButton
                        variante="fantasma"
                        type="button"
                        (click)="filtro.set(null)"
                      >
                        Ver todos
                      </button>
                    </app-estado-vazio>
                  }
                </li>
              }
            </ul>
          </section>
        </div>
      }
    }
  `,
})
export class UbsDetailPage {
  private readonly ubsService = inject(UbsService);
  private readonly preferencias = inject(PreferenciasService);

  protected readonly icones = ICONE;
  protected readonly iconesVazio = ICONES_VAZIO;
  protected readonly status = ORDEM_STATUS;
  protected readonly rotulo = STATUS_LABEL;
  protected readonly rota = linkRota;

  /** Vem do parâmetro de rota `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });

  /** Situação escolhida nos chips; `null` = todas. */
  protected readonly filtro = signal<StatusEstoque | null>(null);

  protected readonly referencia = computed(() => this.preferencias.ubsReferenciaId() === this.id());

  private readonly tentativa = signal(0);

  protected readonly estado = toSignal(
    toObservable(computed(() => ({ id: this.id(), tentativa: this.tentativa() }))).pipe(
      // Recarrega a cada ATUALIZAR_DADOS_A_CADA_MS enquanto a tela está aberta; se uma recarga falhar, fica o último estoque.
      switchMap(({ id }) =>
        interval(ATUALIZAR_DADOS_A_CADA_MS).pipe(
          startWith(0),
          switchMap(() =>
            forkJoin({
              ubs: this.ubsService.buscarPorId(id),
              estoque: this.ubsService.estoque(id),
            }).pipe(
              map(({ ubs, estoque }): Estado => ({ tipo: 'ok', ubs, estoque })),
              catchError((erro: unknown) =>
                of<Estado>({
                  tipo:
                    erro instanceof HttpErrorResponse && erro.status === 404
                      ? 'nao-encontrada'
                      : 'erro',
                }),
              ),
            ),
          ),
          scan(
            (anterior: Estado, novo: Estado): Estado =>
              novo.tipo === 'erro' && anterior.tipo === 'ok' ? anterior : novo,
            { tipo: 'carregando' } as Estado,
          ),
          startWith<Estado>({ tipo: 'carregando' }),
        ),
      ),
    ),
    { initialValue: { tipo: 'carregando' } as Estado },
  );

  private readonly ubs = computed(() => {
    const e = this.estado();
    return e.tipo === 'ok' ? e.ubs : null;
  });

  protected readonly sincronizacao = computed(() => {
    const min = minutosDesde(this.ubs()?.ultimaSincronizacao ?? null);
    return {
      nivel: nivelSincronizacao(min),
      titulo:
        min === null
          ? 'Esta unidade ainda não sincronizou o estoque.'
          : `Última sincronização ${tempoDesde(min)}.`,
    };
  });

  /** `tel:` só com dígitos (e o `+` do DDI), para o discador do celular. */
  protected readonly telefoneLink = computed(() => {
    const digitos = this.ubs()?.telefone?.replace(/[^\d+]/g, '');
    return digitos ? `tel:${digitos}` : null;
  });

  /** Itens com o status exibido (DISPONIVEL sem quantidade conta como em falta), por nome. */
  private readonly itens = computed(() => {
    const e = this.estado();
    if (e.tipo !== 'ok') return [];
    return e.estoque
      .map((item) => ({ item, status: statusExibido(item) }))
      .sort((a, b) =>
        `${a.item.medicamento.nome} ${a.item.medicamento.concentracao}`.localeCompare(
          `${b.item.medicamento.nome} ${b.item.medicamento.concentracao}`,
          'pt-BR',
        ),
      );
  });

  protected readonly contagem = computed(() => {
    const c: Record<StatusEstoque, number> = { DISPONIVEL: 0, RESERVADO: 0, EM_FALTA: 0 };
    for (const { status } of this.itens()) c[status]++;
    return c;
  });

  protected readonly linhas = computed(() => {
    const f = this.filtro();
    return this.itens()
      .filter(({ status }) => f === null || status === f)
      .map(({ item, status }) => ({
        item,
        status,
        detalhe:
          status === 'DISPONIVEL'
            ? `~${item.quantidade} un. em estoque`
            : status === 'RESERVADO'
              ? `${item.quantidade} un. reservadas`
              : 'Sem estoque',
      }));
  });

  protected recarregar(): void {
    this.tentativa.update((n) => n + 1);
  }

  protected alternarReferencia(): void {
    this.preferencias.definirUbsReferencia(this.referencia() ? null : this.id());
  }
}
