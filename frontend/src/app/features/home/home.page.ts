import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, FormsModule],
  template: `
    <section class="surface-card p-6">
      <p class="body-muted">Consulta de estoque nas Unidades Básicas de Saúde</p>
      <h1 class="mt-1 text-2xl font-bold tracking-tight">Qual remédio você procura?</h1>
      <form class="mt-5 flex gap-2" (ngSubmit)="buscar()">
        <input
          name="termo"
          [(ngModel)]="termo"
          type="search"
          placeholder="Ex.: Metformina"
          aria-label="Nome do medicamento"
          class="control flex-1 bg-canvas"
        />
        <button
          type="submit"
          class="h-12 rounded-xl bg-brand-600 px-5 font-bold text-white hover:bg-brand-700"
        >
          Buscar
        </button>
      </form>
    </section>

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
