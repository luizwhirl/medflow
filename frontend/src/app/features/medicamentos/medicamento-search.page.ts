import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { List } from '../../shared/components/list';
import { SearchField } from '../../shared/components/search-field';

@Component({
  selector: 'app-medicamento-search-page',
  imports: [RouterLink, SearchField, List],
  template: `
    <h1 class="page-title mb-3">Medicamentos</h1>
    <app-search-field
      placeholder="Buscar por nome ou categoria"
      [valor]="termo()"
      (valorChange)="termo.set($event)"
    />
    <app-list
      class="mt-3 block"
      [vazia]="resultados().length === 0"
      vazio="Nenhum medicamento encontrado."
    >
      @for (m of resultados(); track m.id) {
        <li>
          <a [routerLink]="['/medicamentos', m.id]" class="interactive-row px-4 py-3">
            <div class="font-semibold">{{ m.nome }} {{ m.concentracao }}</div>
            <div class="body-muted">
              {{ m.formaFarmaceutica }} · {{ m.categoria }} · CATMAT {{ m.catmat }}
            </div>
          </a>
        </li>
      }
    </app-list>
  `,
})
export class MedicamentoSearchPage {
  private readonly medicamentoService = inject(MedicamentoService);

  /** Query param `?busca=` (withComponentInputBinding). */
  readonly busca = input<string>('');
  /** Query param `?categoria=` (atalhos da tela inicial). */
  readonly categoria = input<string>('');
  protected readonly termo = linkedSignal(() => this.busca() ?? '');

  protected readonly resultados = toSignal(
    toObservable(
      computed(() => ({ termo: this.termo().trim(), categoria: this.categoria() ?? '' })),
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
}
