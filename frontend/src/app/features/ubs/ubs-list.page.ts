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
    <h1 class="page-title mb-3">Unidades de saúde</h1>
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando unidades…</p>
    } @else if (e.erro) {
      <p class="feedback-error">
        Não foi possível carregar as unidades. Verifique se o backend está rodando.
      </p>
    } @else {
      <ul class="surface-list">
        @for (u of e.ubs; track u.id) {
          <li>
            <a [routerLink]="['/ubs', u.id]" class="interactive-row px-4 py-3">
              <div class="font-semibold">{{ u.nome }}</div>
              <div class="body-muted">{{ u.endereco }} · {{ u.bairro }}</div>
              <app-sync-indicator class="mt-1 block" [em]="u.ultimaSincronizacao" />
            </a>
          </li>
        } @empty {
          <li class="body-muted p-4">Nenhuma unidade cadastrada.</li>
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
