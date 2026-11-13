import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';
import { SearchField } from '../../shared/components/search-field';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, Card, SearchField, Button],
  template: `
    <app-card pad="lg">
      <p class="body-muted">Consulta de estoque nas Unidades Básicas de Saúde</p>
      <h1 class="mt-1 text-2xl font-bold tracking-tight">Qual remédio você procura?</h1>
      <div class="mt-5 flex gap-2">
        <app-search-field
          class="flex-1"
          placeholder="Ex.: Metformina"
          [(valor)]="termo"
          (buscar)="buscar()"
        />
        <button appButton type="button" (click)="buscar()">Buscar</button>
      </div>
    </app-card>

    <section class="mt-4 grid gap-4 sm:grid-cols-2">
      <a
        routerLink="/ubs"
        class="surface-card p-5 transition-shadow hover:ring-2 hover:ring-brand-100"
      >
        <h2 class="font-bold">UBS perto de você</h2>
        <p class="mt-1 body-muted">Veja as unidades e o estoque de cada farmácia.</p>
      </a>
      <a
        routerLink="/medicamentos"
        class="surface-card p-5 transition-shadow hover:ring-2 hover:ring-brand-100"
      >
        <h2 class="font-bold">Medicamentos</h2>
        <p class="mt-1 body-muted">Descubra em quais unidades o remédio está disponível.</p>
      </a>
    </section>
  `,
})
export class HomePage {
  private readonly router = inject(Router);
  protected termo = '';

  protected buscar(): void {
    this.router.navigate(['/medicamentos'], { queryParams: { busca: this.termo || null } });
  }
}
