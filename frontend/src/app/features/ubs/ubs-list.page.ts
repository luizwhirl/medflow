import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';

import { Ubs } from '../../core/models/ubs.model';
import { UbsService } from '../../core/services/ubs.service';
import { SyncIndicator } from '../../shared/components/sync-indicator';

type Estado = { carregando: true } | { carregando: false; ubs: Ubs[]; erro: boolean };

@Component({
  selector: 'app-ubs-list-page',
  imports: [RouterLink, SyncIndicator],
  template: `
    <h1 class="mb-3 text-xl font-bold">Unidades de saúde</h1>
    @let e = estado();
    @if (e.carregando) {
      <p class="text-slate-500">Carregando unidades…</p>
    } @else if (e.erro) {
      <p class="rounded-xl bg-red-50 p-4 text-sm text-red-800">
        Não foi possível carregar as unidades. Verifique se o backend está rodando.
      </p>
    } @else {
      <ul class="divide-y divide-slate-200 overflow-hidden rounded-2xl bg-white shadow-sm">
        @for (u of e.ubs; track u.id) {
          <li>
            <a [routerLink]="['/ubs', u.id]" class="block px-4 py-3 hover:bg-slate-50">
              <div class="font-semibold">{{ u.nome }}</div>
              <div class="text-sm text-slate-500">{{ u.endereco }} · {{ u.bairro }}</div>
              <app-sync-indicator class="mt-1 block" [em]="u.ultimaSincronizacao" />
            </a>
          </li>
        } @empty {
          <li class="p-4 text-slate-500">Nenhuma unidade cadastrada.</li>
        }
      </ul>
    }
  `,
})
export class UbsListPage {
  private readonly ubsService = inject(UbsService);

  protected readonly estado = toSignal(
    this.ubsService.listar().pipe(
      map((ubs): Estado => ({ carregando: false, ubs, erro: false })),
      catchError(() => of<Estado>({ carregando: false, ubs: [], erro: true })),
      startWith<Estado>({ carregando: true }),
    ),
    { requireSync: true },
  );
}
