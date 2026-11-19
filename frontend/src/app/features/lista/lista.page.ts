import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, Observable, of, startWith, switchMap } from 'rxjs';

import { compararDistancia, Coordenadas, formatarDistancia, posicaoAtual } from '../../core/geo';
import { Disponibilidade, statusExibido, temEstoque } from '../../core/models/estoque.model';
import { ItemListaDesejo } from '../../core/models/lista-desejos.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { ListaDesejosService } from '../../core/services/lista-desejos.service';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { StatusBadge } from '../../shared/components/status-badge';

interface Detalhe {
  medicamento: Medicamento | null;
  disponibilidade: Disponibilidade[];
}

type Carga =
  | { tipo: 'carregando' }
  | { tipo: 'erro' }
  | { tipo: 'ok'; itens: ItemListaDesejo[]; detalhes: Map<number, Detalhe> };

interface Linha {
  item: ItemListaDesejo;
  nome: string;
  status: StatusEstoque | null;
  previsao: string;
  /** Com estoque na própria UBS do item, não há alternativa a mostrar. */
  temNaUbs: boolean;
  alternativa: { ubs: Ubs; distancia: number | null } | null;
  ocupado: boolean;
}

const ICONE = {
  pilula: 'M10.5 20.5 3.5 13.5a4.95 4.95 0 0 1 7-7l7 7a4.95 4.95 0 0 1-7 7zM7 10l7 7',
  lixeira: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3',
  marcador: 'M6 3h12v18l-6-4-6 4z',
} as const;

@Component({
  selector: 'app-lista-page',
  imports: [NgTemplateOutlet, RouterLink, StatusBadge],
  template: `
    <ng-template #icone let-d let-tam="tam">
      <svg
        [class]="(tam ?? 'size-5') + ' shrink-0'"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path [attr.d]="d" />
      </svg>
    </ng-template>

    <ng-template #pilula>
      <span
        class="grid size-12 shrink-0 place-items-center rounded-control bg-brand-50 text-brand-600"
      >
        <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.pilula }" />
      </span>
    </ng-template>

    <ng-template #tplInterruptor let-l>
      <button
        type="button"
        role="switch"
        [attr.aria-checked]="l.item.notificar"
        [attr.aria-label]="'Avisar quando ' + l.nome + ' chegar'"
        [disabled]="l.ocupado"
        (click)="alternarAviso(l.item)"
        class="inline-flex items-center gap-3 text-sm font-semibold disabled:opacity-50"
      >
        <span
          class="relative h-6 w-11 shrink-0 rounded-full transition-colors"
          [class]="l.item.notificar ? 'bg-brand-600' : 'bg-outline-strong'"
        >
          <span
            class="absolute top-0.5 size-5 rounded-full bg-white shadow transition-all"
            [class]="l.item.notificar ? 'left-5.5' : 'left-0.5'"
          ></span>
        </span>
        <span aria-hidden="true" class="xl:sr-only">Avisar quando chegar</span>
      </button>
    </ng-template>

    <ng-template #tplRemover let-l>
      <button
        type="button"
        class="grid size-11 shrink-0 place-items-center rounded-control text-content-muted transition-colors hover:bg-status-falta-subtle hover:text-status-falta disabled:opacity-50"
        [attr.aria-label]="'Remover ' + l.nome + ' da lista'"
        [disabled]="l.ocupado"
        (click)="remover(l.item)"
      >
        <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.lixeira }" />
      </button>
    </ng-template>

    <ng-template #tplOndeTem let-l>
      @if (l.temNaUbs) {
        <span class="text-content-muted">Na própria UBS</span>
      } @else if (l.alternativa; as a) {
        <a [routerLink]="['/ubs', a.ubs.id]" class="font-semibold text-brand-600 hover:underline">
          {{ a.ubs.nome }}{{ a.distancia !== null ? ' · ' + distancia(a.distancia) : '' }}
        </a>
      } @else {
        <span class="text-content-muted">Nenhuma UBS com estoque</span>
      }
    </ng-template>

    <header>
      <h1 class="text-3xl font-bold tracking-tight">Minha lista</h1>
      <p class="mt-1 text-content-muted">
        Remédios que você acompanha, com o estoque de cada UBS e onde encontrar agora.
      </p>
    </header>

    <div
      class="mt-5 flex items-start gap-3 rounded-card bg-brand-50 px-5 py-4 text-brand-700"
      data-aviso="referencia"
    >
      <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.marcador }" />
      <p>
        @if (ubsReferencia(); as u) {
          Novos remédios ficam vinculados à <b>{{ u.nome }}</b
          >, sua UBS de referência.
        } @else {
          Escolha sua UBS de referência para vincular novos remédios a ela.
          <a routerLink="/mapa" class="font-semibold underline">Escolher UBS</a>
        }
      </p>
    </div>

    <p class="sr-only" aria-live="polite">{{ anuncio() }}</p>
    @if (erroAcao(); as msg) {
      <p class="feedback-error mt-4" role="alert">{{ msg }}</p>
    }

    @let c = carga();
    @switch (c.tipo) {
      @case ('carregando') {
        <p class="body-muted mt-6">Carregando sua lista…</p>
      }
      @case ('erro') {
        <div class="feedback-error mt-6 flex flex-wrap items-center justify-between gap-3">
          <span>Não foi possível carregar sua lista agora. Verifique a conexão.</span>
          <button type="button" class="font-semibold underline" (click)="recarregar()">
            Tentar de novo
          </button>
        </div>
      }
      @case ('ok') {
        @if (linhas().length === 0) {
          <div class="surface-card mt-6 flex flex-col items-center p-10 text-center">
            <span class="grid size-14 place-items-center rounded-full bg-brand-50 text-brand-600">
              <ng-container
                *ngTemplateOutlet="icone; context: { $implicit: icones.marcador, tam: 'size-7' }"
              />
            </span>
            <h2 class="mt-4 text-lg font-bold">Sua lista está vazia</h2>
            <p class="mt-1 max-w-sm text-content-muted">
              Busque um medicamento e toque em “Avisar quando chegar” para acompanhá-lo aqui.
            </p>
            <a
              routerLink="/medicamentos"
              class="mt-5 inline-flex h-12 items-center rounded-control bg-brand-600 px-5 font-bold text-white hover:bg-brand-700"
              >Buscar medicamento</a
            >
          </div>
        } @else {
          <!-- Desktop: tabela, como "Meus remédios de uso contínuo" da Início -->
          <div class="surface-card mt-6 hidden overflow-hidden xl:block">
            <table class="w-full text-left">
              <thead
                class="border-b border-outline text-xs font-semibold uppercase tracking-wider text-content-muted"
              >
                <tr>
                  <th class="px-7 py-4" colspan="2">Medicamento</th>
                  <th class="px-3 py-4">Na UBS</th>
                  <th class="px-3 py-4">Previsão / estoque</th>
                  <th class="px-3 py-4">Onde tem agora</th>
                  <th class="px-3 py-4">Aviso</th>
                  <th class="py-4 pl-3 pr-7"><span class="sr-only">Remover</span></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-outline">
                @for (l of linhas(); track l.item.id) {
                  <tr>
                    <td class="w-0 py-5 pl-7 pr-4">
                      <ng-container *ngTemplateOutlet="pilula" />
                    </td>
                    <td class="py-5 pr-3">
                      <a
                        [routerLink]="['/medicamentos', l.item.medicamentoId]"
                        class="font-semibold hover:underline"
                        >{{ l.nome }}</a
                      >
                      <span class="block text-sm text-content-muted">{{ l.item.ubsNome }}</span>
                    </td>
                    <td class="px-3 py-5">
                      @if (l.status) {
                        <app-status-badge [status]="l.status" />
                      } @else {
                        <span class="text-sm text-content-muted">Sem registro</span>
                      }
                    </td>
                    <td class="px-3 py-5 text-sm text-content-muted">{{ l.previsao }}</td>
                    <td class="px-3 py-5 text-sm">
                      <ng-container *ngTemplateOutlet="tplOndeTem; context: { $implicit: l }" />
                    </td>
                    <td class="px-3 py-5">
                      <ng-container *ngTemplateOutlet="tplInterruptor; context: { $implicit: l }" />
                    </td>
                    <td class="py-5 pl-3 pr-7 text-right">
                      <ng-container *ngTemplateOutlet="tplRemover; context: { $implicit: l }" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <a
              routerLink="/medicamentos"
              class="block border-t border-outline px-7 py-4 font-semibold text-brand-600 hover:bg-canvas"
              >+ Adicionar medicamento</a
            >
          </div>

          <!-- Celular e tablet: cartões -->
          <ul class="mt-6 grid gap-4 sm:grid-cols-2 xl:hidden" aria-label="Remédios da lista">
            @for (l of linhas(); track l.item.id) {
              <li class="surface-card flex flex-col p-5 sm:p-6">
                <div class="flex items-start justify-between gap-2">
                  <ng-container *ngTemplateOutlet="pilula" />
                  @if (l.status) {
                    <app-status-badge [status]="l.status" tamanho="md" />
                  } @else {
                    <span class="text-sm text-content-muted">Sem registro</span>
                  }
                </div>
                <h2 class="mt-4 text-lg font-bold">
                  <a
                    [routerLink]="['/medicamentos', l.item.medicamentoId]"
                    class="hover:underline"
                    >{{ l.nome }}</a
                  >
                </h2>
                <p class="text-sm text-content-muted">{{ l.item.ubsNome }}</p>
                <p class="mt-2 text-content-muted">{{ l.previsao }}</p>
                <p class="mb-4 mt-1">
                  <ng-container *ngTemplateOutlet="tplOndeTem; context: { $implicit: l }" />
                </p>
                <div
                  class="mt-auto flex items-center justify-between gap-3 border-t border-outline pt-3"
                >
                  <ng-container *ngTemplateOutlet="tplInterruptor; context: { $implicit: l }" />
                  <ng-container *ngTemplateOutlet="tplRemover; context: { $implicit: l }" />
                </div>
              </li>
            }
            <li>
              <a
                routerLink="/medicamentos"
                class="grid h-full min-h-40 place-items-center rounded-card border-2 border-dashed border-outline p-6 text-lg font-semibold text-content-muted hover:border-brand-500 hover:text-brand-600"
              >
                + Adicionar medicamento
              </a>
            </li>
          </ul>
        }
      }
    }
  `,
})
export class ListaPage {
  private readonly listaService = inject(ListaDesejosService);
  private readonly medicamentoService = inject(MedicamentoService);
  private readonly ubsService = inject(UbsService);
  private readonly preferencias = inject(PreferenciasService);

  protected readonly icones = ICONE;
  protected readonly distancia = formatarDistancia;

  private readonly tentativa = signal(0);
  private readonly posicao = signal<Coordenadas | null>(null);
  /** Ids dos itens com uma alteração em andamento (botões desativados). */
  private readonly ocupados = signal<ReadonlySet<number>>(new Set());
  protected readonly erroAcao = signal<string | null>(null);
  protected readonly anuncio = signal('');

  /** Lista da API e, para cada medicamento, o nome completo e a disponibilidade nas UBS. */
  protected readonly carga = toSignal(
    toObservable(this.tentativa).pipe(
      switchMap(() =>
        this.listaService.listar().pipe(
          switchMap((itens) => {
            const ids = [...new Set(itens.map((i) => i.medicamentoId))];
            const detalhes = ids.length
              ? forkJoin(ids.map((id) => this.detalhe(id)))
              : of([] as Detalhe[]);
            return detalhes.pipe(
              map((d): Carga => ({
                tipo: 'ok',
                itens,
                detalhes: new Map(ids.map((id, i) => [id, d[i]])),
              })),
            );
          }),
          catchError(() => of<Carga>({ tipo: 'erro' })),
          startWith<Carga>({ tipo: 'carregando' }),
        ),
      ),
    ),
    { initialValue: { tipo: 'carregando' } as Carga },
  );

  /** Itens exibidos; ligar/desligar o aviso e remover mudam só esta cópia, sem recarregar tudo. */
  private readonly itens = linkedSignal(() => {
    const c = this.carga();
    return c.tipo === 'ok' ? c.itens : [];
  });

  /** Distância (km) da API por id de UBS, quando há localização (raio padrão de 20 km). */
  private readonly distancias = toSignal(
    toObservable(this.posicao).pipe(
      switchMap((pos) =>
        pos
          ? this.ubsService.proximas(pos).pipe(
              map((ubs) => new Map(ubs.map((u) => [u.id, u.distanciaKm]))),
              catchError(() => of(new Map<number, number>())),
            )
          : of(new Map<number, number>()),
      ),
    ),
    { initialValue: new Map<number, number>() },
  );

  protected readonly ubsReferencia = toSignal(
    toObservable(computed(() => this.preferencias.ubsReferenciaId())).pipe(
      switchMap((id) =>
        id ? this.ubsService.buscarPorId(id).pipe(catchError(() => of(null))) : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly linhas = computed((): Linha[] => {
    const c = this.carga();
    const detalhes = c.tipo === 'ok' ? c.detalhes : new Map<number, Detalhe>();
    const distancias = this.distancias();
    const ocupados = this.ocupados();
    return this.itens().map((item) => {
      const det = detalhes.get(item.medicamentoId);
      const naUbs = det?.disponibilidade.find((d) => d.ubs.id === item.ubsId) ?? null;
      const temNaUbs = naUbs !== null && temEstoque(naUbs);
      const alternativa = temNaUbs
        ? null
        : ((det?.disponibilidade ?? [])
            .filter((d) => temEstoque(d) && d.ubs.id !== item.ubsId)
            .map((d) => ({ ubs: d.ubs, distancia: distancias.get(d.ubs.id) ?? null }))
            .sort((a, b) => compararDistancia(a.distancia, b.distancia))[0] ?? null);
      const m = det?.medicamento;
      return {
        item,
        nome: m ? `${m.nome} ${m.concentracao}` : item.medicamentoNome,
        status: naUbs ? statusExibido(naUbs) : null,
        previsao: temNaUbs
          ? `~${naUbs!.quantidade} un. em estoque`
          : naUbs?.status === 'RESERVADO'
            ? `${naUbs.quantidade} un. reservadas`
            : 'Sem previsão de remessa',
        temNaUbs,
        alternativa,
        ocupado: ocupados.has(item.id),
      };
    });
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  protected recarregar(): void {
    this.erroAcao.set(null);
    this.tentativa.update((n) => n + 1);
  }

  protected alternarAviso(item: ItemListaDesejo): void {
    const nome = this.nome(item);
    this.executar(
      item,
      nome,
      this.listaService.definirAviso(item.id, !item.notificar),
      (atualizado) => {
        this.itens.update((lista) => lista.map((i) => (i.id === atualizado.id ? atualizado : i)));
        this.anuncio.set(`Aviso ${atualizado.notificar ? 'ligado' : 'desligado'} para ${nome}.`);
      },
    );
  }

  protected remover(item: ItemListaDesejo): void {
    // O nome completo (com a concentração) vem da linha, que some da lista ao remover.
    const nome = this.nome(item);
    this.executar(item, nome, this.listaService.remover(item.id), () => {
      this.itens.update((lista) => lista.filter((i) => i.id !== item.id));
      this.anuncio.set(`${nome} removido da sua lista.`);
    });
  }

  private executar<T>(
    item: ItemListaDesejo,
    nome: string,
    chamada: Observable<T>,
    sucesso: (r: T) => void,
  ): void {
    this.erroAcao.set(null);
    this.ocupados.update((s) => new Set(s).add(item.id));
    chamada
      .pipe(
        finalize(() =>
          this.ocupados.update((s) => {
            const novo = new Set(s);
            novo.delete(item.id);
            return novo;
          }),
        ),
      )
      .subscribe({
        next: sucesso,
        error: () => this.erroAcao.set(`Não foi possível alterar ${nome} agora. Tente de novo.`),
      });
  }

  private nome(item: ItemListaDesejo): string {
    return this.linhas().find((l) => l.item.id === item.id)?.nome ?? item.medicamentoNome;
  }

  private detalhe(id: number) {
    return forkJoin({
      medicamento: this.medicamentoService.buscarPorId(id).pipe(catchError(() => of(null))),
      disponibilidade: this.medicamentoService
        .disponibilidade(id)
        .pipe(catchError(() => of([] as Disponibilidade[]))),
    });
  }
}
