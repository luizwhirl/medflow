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
    <h1 class="mb-3 text-xl font-bold">Medicamentos</h1>
    <input
      type="search"
      [ngModel]="termo()"
      (ngModelChange)="termo.set($event)"
      placeholder="Buscar por nome ou categoria"
      aria-label="Buscar medicamento"
      class="h-12 w-full rounded-xl border border-slate-200 bg-white px-4 outline-none focus:border-brand-500"
    />
    <ul class="mt-3 divide-y divide-slate-200 overflow-hidden rounded-2xl bg-white shadow-sm">
      @for (m of resultados(); track m.id) {
        <li>
          <a [routerLink]="['/medicamentos', m.id]" class="block px-4 py-3 hover:bg-slate-50">
            <div class="font-semibold">{{ m.nome }} {{ m.concentracao }}</div>
            <div class="text-sm text-slate-500">
              {{ m.formaFarmaceutica }} · {{ m.categoria }} · CATMAT {{ m.catmat }}
            </div>
          </a>
        </li>
      } @empty {
        <li class="p-4 text-slate-500">Nenhum medicamento encontrado.</li>
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
