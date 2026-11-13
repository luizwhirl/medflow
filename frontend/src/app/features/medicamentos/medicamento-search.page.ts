import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { Chip } from '../../shared/components/chip';
import { List } from '../../shared/components/list';
import { SearchField } from '../../shared/components/search-field';
import { RastreioMedicamento } from './rastreio-medicamento';

/**
 * Buscar e rastrear. No desktop/tablet paisagem, lista à esquerda e detalhe à direita;
 * no celular e tablet retrato, o detalhe substitui a lista (com "Voltar").
 */
@Component({
  selector: 'app-medicamento-search-page',
  imports: [RouterLink, SearchField, List, Chip, RastreioMedicamento],
  template: `
    <div class="grid gap-6 md:grid-cols-[minmax(0,24rem)_minmax(0,1fr)]">
      <section class="min-w-0" [class]="selecionado() ? 'hidden md:block' : ''">
        <h1 class="page-title mb-3">Buscar remédio</h1>
        <app-search-field
          placeholder="Buscar por nome ou categoria"
          [valor]="termo()"
          (valorChange)="termo.set($event)"
        />
        @if (categorias().length) {
          <div
            class="-mx-4 mt-3 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0"
            role="group"
            aria-label="Filtrar por categoria"
          >
            @for (c of categorias(); track c) {
              <app-chip
                [selecionado]="categoriaAtual() === c"
                (selecionadoChange)="filtrarCategoria($event ? c : '')"
                >{{ c }}</app-chip
              >
            }
          </div>
        }
        <app-list
          class="mt-3 block"
          [vazia]="resultados().length === 0"
          vazio="Nenhum medicamento encontrado."
        >
          @for (m of resultados(); track m.id) {
            <li>
              <a
                [routerLink]="[]"
                [queryParams]="{ id: m.id }"
                queryParamsHandling="merge"
                [attr.aria-current]="selecionado() === m.id ? 'true' : null"
                class="interactive-row px-4 py-3"
                [class.bg-brand-50]="selecionado() === m.id"
              >
                <div class="font-semibold">{{ m.nome }} {{ m.concentracao }}</div>
                <div class="body-muted">
                  {{ m.formaFarmaceutica }} · {{ m.categoria }} · CATMAT {{ m.catmat }}
                </div>
              </a>
            </li>
          }
        </app-list>
      </section>

      <section class="min-w-0" [class]="selecionado() ? '' : 'hidden md:block'">
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
            class="hidden h-full min-h-60 place-items-center rounded-card border-2 border-dashed border-outline p-6 text-center md:grid"
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
  private readonly router = inject(Router);

  /** Query params (withComponentInputBinding). */
  readonly busca = input<string>('');
  readonly categoria = input<string>('');
  readonly id = input<string | undefined>(undefined);

  protected readonly termo = linkedSignal(() => this.busca() ?? '');
  protected readonly categoriaAtual = linkedSignal(() => this.categoria() ?? '');
  protected readonly selecionado = computed(() => {
    const n = Number(this.id());
    return Number.isInteger(n) && n > 0 ? n : null;
  });

  protected readonly categorias = toSignal(
    this.medicamentoService.categorias().pipe(catchError(() => of([]))),
    { initialValue: [] as string[] },
  );

  protected readonly resultados = toSignal(
    toObservable(
      computed(() => ({ termo: this.termo().trim(), categoria: this.categoriaAtual() })),
    ).pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => a.termo === b.termo && a.categoria === b.categoria),
      switchMap(({ termo, categoria }) =>
        this.medicamentoService
          .buscar(termo, categoria)
          .pipe(catchError(() => of<Medicamento[]>([]))),
      ),
    ),
    { initialValue: [] as Medicamento[] },
  );

  protected filtrarCategoria(categoria: string): void {
    this.categoriaAtual.set(categoria);
    this.router.navigate([], {
      queryParams: { categoria: categoria || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
