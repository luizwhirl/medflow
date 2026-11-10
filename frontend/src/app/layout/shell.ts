import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-shell">
      <header class="sticky top-0 z-10 border-b border-outline bg-surface">
        <div class="flex items-center gap-3 px-4 py-3 sm:px-6 md:px-8">
          <a routerLink="/" class="flex items-center gap-2 text-lg font-extrabold tracking-tight">
            <span class="grid size-8 place-items-center rounded-lg bg-brand-600 text-white">+</span>
            MedFlow
          </a>
          <nav
            class="ml-auto flex gap-1 overflow-x-auto text-sm font-semibold"
            aria-label="Principal"
          >
            @for (item of nav; track item.path) {
              <a
                [routerLink]="item.path"
                routerLinkActive="bg-brand-50 text-brand-600"
                [routerLinkActiveOptions]="{ exact: item.exact }"
                class="rounded-control px-3 py-2 text-content-muted hover:bg-canvas"
                >{{ item.label }}</a
              >
            }
          </nav>
        </div>
      </header>
      <main class="flex-1 p-4 sm:p-6 md:p-8">
        <router-outlet />
      </main>
      <footer class="px-4 py-6 text-center text-xs text-content-subtle">
        Dados: BNAFAR / e-SUS AF · identificadores CNES, CATMAT e RENAME
      </footer>
    </div>
  `,
})
export class Shell {
  protected readonly nav = [
    { path: '/', label: 'Início', exact: true },
    { path: '/ubs', label: 'UBS', exact: false },
    { path: '/medicamentos', label: 'Medicamentos', exact: false },
  ];
}
