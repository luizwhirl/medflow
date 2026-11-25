import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';

import { Coordenadas, posicaoAtual } from '../../core/geo';
import { Disponibilidade, temEstoque } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { ResultadoVoz } from '../../core/services/reconhecimento-voz.service';
import { UbsService } from '../../core/services/ubs.service';
import { Chip } from '../../shared/components/chip';
import { SearchField } from '../../shared/components/search-field';
import { RastreioMedicamento } from './rastreio-medicamento';

/** Raio do filtro "Até 5 km". */
export const RAIO_PROXIMAS_KM = 5;
/** Máximo de resultados para os quais buscamos a disponibilidade. */
const MAX_RESULTADOS = 20;

interface Resultado {
  medicamento: Medicamento;
  /** Unidades consideradas (todas, ou só as do raio quando o filtro está ligado). */
  total: number;
  disponiveis: number;
}

/**
 * Buscar e rastrear (Figma: "Desktop · Buscar e rastrear").
 * Desktop/tablet paisagem: lista à esquerda e detalhe à direita;
 * celular e tablet retrato: o detalhe substitui a lista (com "Voltar").
 */
@Component({
  selector: 'app-medicamento-search-page',
  imports: [RouterLink, SearchField, Chip, RastreioMedicamento],
  template: `
    <div class="md:-my-8 md:grid md:min-h-dvh md:grid-cols-[21rem_minmax(0,1fr)] xl:-ml-14">
      <section
        class="min-w-0 md:-ml-8 md:border-r md:border-outline md:bg-surface md:px-7 md:py-8 xl:ml-0"
        [class]="selecionado() ? 'max-md:hidden' : ''"
      >
        <app-search-field
          rotulo="Buscar medicamento"
          [rotuloVisivel]="true"
          placeholder="Ex.: Metformina"
          [valor]="termo()"
          (valorChange)="termo.set($event)"
          (buscar)="preferencias.registrarBusca($event)"
          (voz)="voz.set($event)"
        />
        <div
          class="-mx-4 mt-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] md:-mx-7 md:px-7"
          role="group"
          aria-label="Filtros"
        >
          @if (posicao()) {
            <app-chip [(selecionado)]="ateCincoKm">Até {{ raio }} km</app-chip>
          }
          <app-chip [(selecionado)]="soDisponiveis">Só disponíveis</app-chip>
          <app-chip [(selecionado)]="abertasAgora">Abertas agora</app-chip>
          @for (c of categorias(); track c) {
            <app-chip
              [selecionado]="categoriaAtual() === c"
              (selecionadoChange)="filtrarCategoria($event ? c : '')"
              >{{ c }}</app-chip
            >
          }
        </div>

        <p class="mt-4 text-sm text-content-muted" aria-live="polite">
          {{ visiveis().length }} {{ visiveis().length === 1 ? 'resultado' : 'resultados' }}
          @if (encontrados().total > encontrados().itens.length) {
            · mostrando os {{ encontrados().itens.length }} primeiros de {{ encontrados().total }};
            refine a busca
          }
        </p>

        <ul class="mt-3 flex flex-col gap-3" aria-label="Resultados">
          @for (r of visiveis(); track r.medicamento.id) {
            @let ativo = selecionado() === r.medicamento.id;
            <li>
              <a
                [routerLink]="[]"
                [queryParams]="{ id: r.medicamento.id }"
                queryParamsHandling="merge"
                [attr.aria-current]="ativo ? 'true' : null"
                class="block rounded-card border px-5 py-4 transition-colors"
                [class]="
                  ativo
                    ? 'border-2 border-brand-600 bg-brand-50'
                    : 'border-outline bg-surface hover:border-brand-500'
                "
              >
                <span class="block text-lg font-semibold">
                  {{ r.medicamento.nome }} {{ r.medicamento.concentracao }}
                </span>
                @if (preferencias.remediosContinuos().includes(r.medicamento.id)) {
                  <span
                    class="mt-1 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-600"
                    >Na minha lista</span
                  >
                }
                <span class="block capitalize text-content-muted">{{
                  r.medicamento.formaFarmaceutica
                }}</span>
                <span
                  class="mt-1 block font-semibold"
                  [class]="r.disponiveis ? 'text-brand-600' : 'text-status-falta'"
                >
                  @if (r.total === 0) {
                    @if (abertasAgora()) {
                      Nenhuma UBS{{ raioAtivo() ? ' a até ' + raio + ' km' : '' }} aberta agora
                    } @else {
                      {{
                        raioAtivo()
                          ? 'Nenhuma UBS a até ' + raio + ' km'
                          : 'Nenhuma UBS informou estoque'
                      }}
                    }
                  } @else if (r.disponiveis) {
                    Em {{ r.disponiveis }} de {{ r.total }} UBS{{ sufixoUbs() }}
                  } @else {
                    Em falta nas UBS{{ sufixoUbs() }}
                  }
                </span>
              </a>
            </li>
          } @empty {
            <li class="body-muted">Nenhum medicamento encontrado.</li>
          }
        </ul>
      </section>

      <section
        class="min-w-0 md:px-8 md:py-8 xl:px-12"
        [class]="selecionado() ? '' : 'max-md:hidden'"
      >
        @if (selecionado(); as id) {
          <a
            [routerLink]="[]"
            [queryParams]="{ id: null }"
            queryParamsHandling="merge"
            class="mb-3 inline-block text-sm font-semibold text-brand-600 md:hidden"
            >← Voltar à busca</a
          >
          <app-rastreio-medicamento [id]="id" />
        } @else {
          <div
            class="grid h-full min-h-60 place-items-center rounded-card border-2 border-dashed border-outline p-6 text-center"
          >
            <p class="body-muted">Escolha um medicamento para ver onde encontrar e a remessa.</p>
          </div>
        }
      </section>
    </div>
  `,
})
export class MedicamentoSearchPage {
  private readonly medicamentoService = inject(MedicamentoService);
  private readonly ubsService = inject(UbsService);
  private readonly router = inject(Router);
  protected readonly preferencias = inject(PreferenciasService);

  /** Query params (withComponentInputBinding). */
  readonly busca = input<string>('');
  readonly categoria = input<string>('');
  readonly id = input<string | undefined>(undefined);

  protected readonly raio = RAIO_PROXIMAS_KM;
  protected readonly termo = linkedSignal(() => this.busca() ?? '');
  protected readonly categoriaAtual = linkedSignal(() => this.categoria() ?? '');
  protected readonly ateCincoKm = signal(true);
  protected readonly soDisponiveis = signal(false);
  protected readonly abertasAgora = signal(false);
  protected readonly posicao = signal<Coordenadas | null>(null);

  protected readonly raioAtivo = computed(() => this.ateCincoKm() && this.posicao() !== null);

  /**
   * Ids das UBS a até 5 km, segundo a API (uma chamada ao ligar o filtro);
   * `null` com o filtro desligado ou sem localização.
   */
  private readonly idsNoRaio = toSignal(
    toObservable(computed(() => (this.raioAtivo() ? this.posicao() : null))).pipe(
      switchMap((pos) =>
        pos
          ? this.ubsService.proximas(pos, { raioKm: RAIO_PROXIMAS_KM }).pipe(
              map((ubs) => new Set(ubs.map((u) => u.id))),
              catchError(() => of(null)),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  /**
   * Ids das UBS abertas agora, segundo a API (uma chamada ao ligar o chip);
   * `null` com o chip desligado. UBS sem horário cadastrado não entram.
   */
  private readonly idsAbertos = toSignal(
    toObservable(this.abertasAgora).pipe(
      switchMap((ligado) =>
        ligado
          ? this.ubsService.listar({ abertas: true }).pipe(
              map((ubs) => new Set(ubs.map((u) => u.id))),
              catchError(() => of(null)),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  /** Complemento de "Em X de Y UBS": " próximas", " abertas" ou " próximas abertas". */
  protected readonly sufixoUbs = computed(
    () => `${this.raioAtivo() ? ' próximas' : ''}${this.abertasAgora() ? ' abertas' : ''}`,
  );

  protected readonly selecionado = computed(() => {
    const n = Number(this.id());
    return Number.isInteger(n) && n > 0 ? n : null;
  });

  protected readonly categorias = toSignal(
    this.medicamentoService.categorias().pipe(catchError(() => of([]))),
    { initialValue: [] as string[] },
  );

  /** Última busca por voz; as alternativas valem enquanto o campo tiver o texto reconhecido. */
  protected readonly voz = signal<ResultadoVoz | null>(null);
  private readonly alternativas = computed(() => {
    const v = this.voz();
    return v && v.texto.trim() === this.termo().trim() ? v.alternativas : [];
  });

  protected readonly encontrados = toSignal(
    toObservable(
      computed(() => ({
        termo: this.termo().trim(),
        categoria: this.categoriaAtual(),
        alternativas: this.alternativas(),
      })),
    ).pipe(
      debounceTime(250),
      distinctUntilChanged(
        (a, b) =>
          a.termo === b.termo &&
          a.categoria === b.categoria &&
          a.alternativas.join('|') === b.alternativas.join('|'),
      ),
      switchMap(({ termo, categoria, alternativas }) =>
        this.medicamentoService.buscar(termo, categoria, undefined, alternativas).pipe(
          map((pagina) => ({ meds: pagina.itens, total: pagina.totalItens })),
          catchError(() => of({ meds: [] as Medicamento[], total: 0 })),
          switchMap(({ meds, total }) =>
            meds.length === 0
              ? of({ itens: [], total: 0 })
              : forkJoin(
                  meds.slice(0, MAX_RESULTADOS).map((medicamento) =>
                    this.medicamentoService.disponibilidade(medicamento.id).pipe(
                      catchError(() => of<Disponibilidade[]>([])),
                      map((disponibilidade) => ({ medicamento, disponibilidade })),
                    ),
                  ),
                ).pipe(map((itens) => ({ itens, total }))),
          ),
        ),
      ),
    ),
    {
      initialValue: {
        itens: [] as { medicamento: Medicamento; disponibilidade: Disponibilidade[] }[],
        total: 0,
      },
    },
  );

  protected readonly visiveis = computed((): Resultado[] => {
    const noRaio = this.idsNoRaio();
    const abertos = this.idsAbertos();
    return this.encontrados()
      .itens.map(({ medicamento, disponibilidade }) => {
        const consideradas = disponibilidade.filter(
          (d) => (!noRaio || noRaio.has(d.ubs.id)) && (!abertos || abertos.has(d.ubs.id)),
        );
        return {
          medicamento,
          total: consideradas.length,
          disponiveis: consideradas.filter(temEstoque).length,
        };
      })
      .filter((r) => !this.soDisponiveis() || r.disponiveis > 0);
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  protected filtrarCategoria(categoria: string): void {
    this.categoriaAtual.set(categoria);
    this.router.navigate([], {
      queryParams: { categoria: categoria || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
