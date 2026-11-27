import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  catchError,
  distinctUntilChanged,
  filter,
  forkJoin,
  map,
  Observable,
  of,
  startWith,
  switchMap,
  throwError,
} from 'rxjs';

import {
  compararDistancia,
  Coordenadas,
  formatarDistancia,
  linkRota,
  posicaoAtual,
} from '../../core/geo';
import { Disponibilidade, statusExibido, temEstoque } from '../../core/models/estoque.model';
import { ItemListaDesejo } from '../../core/models/lista-desejos.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { CARREGANDO, carregar, dadosOu, Recurso } from '../../core/recurso';
import { RemessaRastreamento } from '../../core/models/rastreamento.model';
import { primeiraProcuraDoDia } from '../../core/buscas-contadas';
import { formatarDia, linhaDoTempo, PASSOS_REMESSA } from '../../core/remessa';
import { Ubs } from '../../core/models/ubs.model';
import { ListaDesejosService } from '../../core/services/lista-desejos.service';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';
import { Esqueleto } from '../../shared/components/esqueleto';
import { EstadoVazio, ICONES_VAZIO } from '../../shared/components/estado-vazio';
import { Funcionamento } from '../../shared/components/funcionamento';
import { MapaIlustrado } from '../../shared/components/mapa-ilustrado';
import { MensagemErro } from '../../shared/components/mensagem-erro';
import { EtapaTimeline, Timeline } from '../../shared/components/timeline';

interface Detalhe {
  medicamento: Medicamento;
  disponibilidade: Disponibilidade[];
}

/** Passos sem datas, para quando não há remessa a mostrar. */
export const ETAPAS_REMESSA: readonly EtapaTimeline[] = PASSOS_REMESSA.map((p) => ({
  titulo: p.titulo,
}));

/** Remessa para a UBS de referência. */
type EstadoRemessa =
  | { tipo: 'carregando' }
  | { tipo: 'sem-referencia' }
  | { tipo: 'erro' }
  | { tipo: 'pronto'; remessa: RemessaRastreamento | null };

const ORDEM = { DISPONIVEL: 0, RESERVADO: 1, EM_FALTA: 2 } as const;

/** Detalhe do medicamento (Figma: "Desktop · Buscar e rastrear", painel da direita). */
@Component({
  selector: 'app-rastreio-medicamento',
  imports: [
    RouterLink,
    Button,
    Esqueleto,
    EstadoVazio,
    Funcionamento,
    MapaIlustrado,
    MensagemErro,
    Timeline,
  ],
  template: `
    @let r = estado();
    @if (r.estado === 'carregando') {
      <app-esqueleto formato="detalhe" [quantidade]="2" rotulo="Carregando o medicamento" />
    } @else if (r.estado === 'erro' && r.naoEncontrado) {
      <app-estado-vazio
        titulo="Medicamento não encontrado"
        texto="Ele pode ter saído do catálogo. Busque pelo nome para encontrar outro."
        [icone]="iconesVazio.pilula"
      >
        <a appButton routerLink="/medicamentos">Buscar medicamento</a>
      </app-estado-vazio>
    } @else if (r.estado === 'erro') {
      <app-mensagem-erro
        titulo="Não foi possível carregar o medicamento"
        (tentarDeNovo)="recarregar()"
      />
    } @else {
      @let e = r.dados;
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
            [attr.aria-pressed]="usoContinuo()"
            title="Remédios de uso contínuo ficam na Minha lista e aparecem na Início e no Mapa"
            [disabled]="
              (!usoContinuo() && !preferencias.ubsReferenciaId()) ||
              desejos() === undefined ||
              salvandoAviso()
            "
            (click)="alternarUsoContinuo()"
          >
            <svg
              class="size-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path [attr.d]="usoContinuo() ? iconeMarcado : iconeUsoContinuo" />
            </svg>
            {{ usoContinuo() ? 'Uso contínuo' : 'Marcar uso contínuo' }}
          </button>
          <button
            appButton
            type="button"
            [variante]="avisoLigado() ? 'secundario' : 'primario'"
            [attr.aria-pressed]="avisoLigado()"
            [disabled]="
              !preferencias.ubsReferenciaId() || desejos() === undefined || salvandoAviso()
            "
            aria-describedby="aviso-ajuda"
            (click)="alternarAviso()"
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
            {{ avisoLigado() ? 'Aviso ligado' : 'Avisar quando chegar' }}
          </button>
          <p id="aviso-ajuda" class="w-full text-right text-xs text-content-muted">
            @if (erroAviso(); as erro) {
              <span class="font-semibold text-status-falta">{{ erro }}</span>
            } @else if (!preferencias.ubsReferenciaId()) {
              <a routerLink="/mapa" class="font-semibold text-brand-600">Escolha sua UBS</a>
              para montar sua lista e receber avisos.
            } @else if (avisoLigado()) {
              Acompanhe em
              <a routerLink="/lista" class="font-semibold text-brand-600">Minha lista</a>.
            } @else {
              O aviso fica vinculado à sua UBS de referência.
            }
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
            @if (avisoRemessa(); as aviso) {
              {{ aviso }}
            }
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
          @if (linha(); as l) {
            <app-timeline [etapas]="l.etapas" [atual]="l.atual" />
          } @else {
            <app-timeline [etapas]="etapas" />
            @switch (remessa().tipo) {
              @case ('carregando') {
                <p class="mt-4 text-sm text-content-muted">Carregando a remessa…</p>
              }
              @case ('sem-referencia') {
                <p class="mt-4 text-sm text-content-muted">
                  <a routerLink="/mapa" class="font-semibold text-brand-600 hover:underline"
                    >Escolha sua UBS</a
                  >
                  para acompanhar a remessa deste remédio.
                </p>
              }
              @case ('erro') {
                <p class="mt-4 text-sm text-content-muted">
                  Não foi possível carregar a remessa agora.
                </p>
              }
              @default {
                <p class="mt-4 text-sm text-content-muted">
                  Nenhuma remessa deste remédio para a sua UBS.
                </p>
              }
            }
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
                    <app-funcionamento class="block text-sm" [ubs]="u.funcionamento" />
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
                <li>
                  <app-estado-vazio
                    [compacto]="true"
                    titulo="Nenhuma unidade informou estoque"
                    texto="Ligue o aviso para saber quando ele chegar na sua UBS."
                    [icone]="iconesVazio.local"
                  />
                </li>
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
  private readonly ubsService = inject(UbsService);
  private readonly listaDesejos = inject(ListaDesejosService);
  protected readonly preferencias = inject(PreferenciasService);

  readonly id = input.required<number>();
  protected readonly etapas = ETAPAS_REMESSA;
  protected readonly iconesVazio = ICONES_VAZIO;
  protected readonly distancia = formatarDistancia;
  protected readonly rota = linkRota;
  private readonly posicao = signal<Coordenadas | null>(null);

  /** Remédio de uso contínuo: está na lista de desejos (Minha lista), em qualquer UBS. */
  protected readonly usoContinuo = computed(() =>
    this.preferencias.remediosContinuos().includes(this.id()),
  );
  protected readonly iconeUsoContinuo =
    'M17 2l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3';
  protected readonly iconeMarcado = 'M5 12.5 9.5 17 19 7';

  /** Lista de desejos deste aparelho; `undefined` enquanto carrega, `null` se falhou. */
  protected readonly desejos = this.listaDesejos.itens;
  protected readonly salvandoAviso = signal(false);
  protected readonly erroAviso = signal<string | null>(null);

  /** Item deste medicamento na UBS de referência, se já estiver na lista. */
  private readonly desejo = computed(() => {
    const ref = this.preferencias.ubsReferenciaId();
    return this.desejos()?.find((d) => d.medicamentoId === this.id() && d.ubsId === ref) ?? null;
  });
  protected readonly avisoLigado = computed(() => this.desejo()?.notificar === true);

  private readonly tentativa = signal(0);

  protected readonly estado = toSignal(
    toObservable(computed(() => ({ id: this.id(), tentativa: this.tentativa() }))).pipe(
      switchMap(({ id }) =>
        carregar(
          forkJoin({
            medicamento: this.medicamentoService.buscarPorId(id),
            disponibilidade: this.medicamentoService.disponibilidade(id),
          }),
        ),
      ),
    ),
    { initialValue: CARREGANDO as Recurso<Detalhe> },
  );

  /** Distância (km) da API, por id de UBS, das unidades no raio padrão que têm o item. */
  private readonly distancias = toSignal(
    toObservable(computed(() => ({ id: this.id(), pos: this.posicao() }))).pipe(
      switchMap(({ id, pos }) =>
        pos
          ? this.ubsService.proximas(pos, { medicamentoId: id }).pipe(
              map((ubs) => new Map(ubs.map((u) => [u.id, u.distanciaKm]))),
              catchError(() => of(new Map<number, number>())),
            )
          : of(new Map<number, number>()),
      ),
    ),
    { initialValue: new Map<number, number>() },
  );

  /**
   * Horário de cada UBS (`abertaAgora`/`fechaAs`/`fechaEm`), por id. A disponibilidade do medicamento não
   * traz o horário, então ele vem da lista de UBS, numa chamada só.
   */
  private readonly funcionamentos = toSignal(
    this.ubsService.listar().pipe(
      map((lista) => new Map(lista.map((u) => [u.id, u]))),
      catchError(() => of(new Map<number, Ubs>())),
    ),
    { initialValue: new Map<number, Ubs>() },
  );

  /** Unidades com o item: disponíveis primeiro, depois pela distância. */
  protected readonly unidades = computed(() => {
    const disponibilidade = dadosOu(this.estado(), null)?.disponibilidade ?? [];
    const distancias = this.distancias();
    const funcionamentos = this.funcionamentos();
    const ref = this.preferencias.ubsReferenciaId();
    return disponibilidade
      .map((d) => ({
        d,
        status: statusExibido(d),
        distancia: distancias.get(d.ubs.id) ?? null,
        referencia: d.ubs.id === ref,
        funcionamento: funcionamentos.get(d.ubs.id) ?? d.ubs,
      }))
      .sort(
        (a, b) => ORDEM[a.status] - ORDEM[b.status] || compararDistancia(a.distancia, b.distancia),
      );
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

  /** Remessa deste remédio para a UBS de referência (uma chamada por remédio e UBS). */
  protected readonly remessa = toSignal(
    toObservable(
      computed(() => ({ id: this.id(), ubsId: this.preferencias.ubsReferenciaId() })),
    ).pipe(
      // outras preferências (uso contínuo, buscas recentes) não devem pedir a remessa de novo
      distinctUntilChanged((a, b) => a.id === b.id && a.ubsId === b.ubsId),
      switchMap(({ id, ubsId }) =>
        ubsId === null
          ? of<EstadoRemessa>({ tipo: 'sem-referencia' })
          : this.medicamentoService.rastreamento(id, ubsId).pipe(
              map((r): EstadoRemessa => ({ tipo: 'pronto', remessa: r.remessa })),
              catchError(() => of<EstadoRemessa>({ tipo: 'erro' })),
              startWith<EstadoRemessa>({ tipo: 'carregando' }),
            ),
      ),
    ),
    { initialValue: { tipo: 'carregando' } as EstadoRemessa },
  );

  private readonly remessaAtual = computed(() => {
    const r = this.remessa();
    return r.tipo === 'pronto' ? r.remessa : null;
  });

  /** Etapas com as datas da API; `null` sem remessa. */
  protected readonly linha = computed(() => {
    const r = this.remessaAtual();
    return r ? linhaDoTempo(r) : null;
  });

  /** Frase sobre a próxima remessa no alerta de falta. */
  protected readonly avisoRemessa = computed(() => {
    const r = this.remessaAtual();
    if (!r || r.concluida) return null;
    const recebida = r.linhaDoTempo.some((e) => e.etapa === 'RECEBIDO' && e.ocorridoEm);
    return recebida
      ? 'A próxima remessa já chegou e está em conferência.'
      : `Próxima remessa chega ${formatarDia(r.previsaoChegada)}.`;
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
    // Uma procura por medicamento aberto, uma vez por dia neste aparelho (recarregar ou reabrir
    // não conta de novo); falhar aqui não atrapalha a tela.
    toObservable(this.id)
      .pipe(
        distinctUntilChanged(),
        filter((id) => primeiraProcuraDoDia(id)),
        switchMap((id) =>
          this.medicamentoService
            .registrarBusca(id, this.preferencias.ubsReferenciaId())
            .pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected recarregar(): void {
    this.tentativa.update((n) => n + 1);
  }

  /** Adiciona à Minha lista (na UBS de referência) ou tira dela. */
  protected alternarUsoContinuo(): void {
    this.salvar(this.preferencias.alternarRemedioContinuo(this.id()), 'a sua lista');
  }

  /**
   * Liga o aviso na UBS de referência: cria o item na lista de desejos ou, se ele já existe,
   * liga/desliga o aviso dele.
   */
  protected alternarAviso(): void {
    const ref = this.preferencias.ubsReferenciaId();
    if (!ref) return;
    const atual = this.desejo();
    const chamada = atual
      ? this.listaDesejos.definirAviso(atual.id, !atual.notificar)
      : this.listaDesejos
          .adicionar(ref, this.id(), true)
          .pipe(catchError((erro) => this.ligarAvisoExistente(erro, ref)));
    this.salvar(chamada, 'o aviso');
  }

  /** A lista de desejos do serviço já reflete a alteração; aqui só o estado dos botões. */
  private salvar(chamada: Observable<unknown>, oQue: string): void {
    this.erroAviso.set(null);
    this.salvandoAviso.set(true);
    chamada.subscribe({
      next: () => this.salvandoAviso.set(false),
      error: () => {
        this.erroAviso.set(`Não foi possível salvar ${oQue} agora. Tente de novo.`);
        this.salvandoAviso.set(false);
      },
    });
  }

  /**
   * 409 ao criar: o item já está na lista, mas não o tínhamos (a lista não carregou ou foi
   * alterada em outra aba). Recarrega a lista e liga o aviso do item que já existe.
   */
  private ligarAvisoExistente(erro: unknown, ubsId: number): Observable<ItemListaDesejo> {
    if (!(erro instanceof HttpErrorResponse && erro.status === 409)) return throwError(() => erro);
    return this.listaDesejos.listar().pipe(
      switchMap((lista) => {
        const existente = lista.find((d) => d.medicamentoId === this.id() && d.ubsId === ubsId);
        if (!existente) return throwError(() => erro);
        return existente.notificar
          ? of(existente)
          : this.listaDesejos.definirAviso(existente.id, true);
      }),
    );
  }
}
