import { Component, inject, input, linkedSignal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { Medicamento } from '../../core/models/medicamento.model';
import { MedicamentoService } from '../../core/services/medicamento.service';

@Component({
  selector: 'app-medicamento-search-page',
  imports: [FormsModule, RouterLink],
  template: `
    <h1 class="page-title mb-3">Medicamentos</h1>
    <input
      type="search"
      [ngModel]="termo()"
      (ngModelChange)="termo.set($event)"
      placeholder="Buscar por nome ou categoria"
      aria-label="Buscar medicamento"
      class="control w-full"
    />
    <ul class="surface-list mt-3">
      @for (m of resultados(); track m.id) {
        <li>
          <a [routerLink]="['/medicamentos', m.id]" class="interactive-row px-4 py-3">
            <div class="font-semibold">{{ m.nome }} {{ m.concentracao }}</div>
            <div class="body-muted">
              {{ m.formaFarmaceutica }} · {{ m.categoria }} · CATMAT {{ m.catmat }}
            </div>
          </a>
        </li>
      } @empty {
        <li class="body-muted p-4">Nenhum medicamento encontrado.</li>
      }
    </ul>
  `,
})
export class MedicamentoSearchPage {
  private readonly medicamentoService = inject(MedicamentoService);

  /** Query param `?busca=` (withComponentInputBinding). */
  readonly busca = input<string>('');
  protected readonly termo = linkedSignal(() => this.busca() ?? '');

  protected readonly resultados = toSignal(
    toObservable(this.termo).pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((t) =>
        this.medicamentoService.buscar(t.trim()).pipe(catchError(() => of<Medicamento[]>([]))),
      ),
    ),
    { initialValue: [] as Medicamento[] },
  );
}
