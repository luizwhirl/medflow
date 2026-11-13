import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { NAV_ITEMS } from './nav-items';

/**
 * Casca da aplicação.
 * - Celular (< sm): barra de abas fixa no rodapé.
 * - Tablet retrato (sm): menu lateral compacto, só ícones.
 * - Tablet paisagem / desktop (>= md): menu lateral com rótulos.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="flex min-h-dvh bg-canvas">
      <aside
        class="sticky top-0 hidden h-dvh shrink-0 flex-col border-r border-outline bg-surface sm:flex sm:w-20 md:w-60"
      >
        <a
          routerLink="/"
          class="flex items-center gap-2 px-4 py-5 text-lg font-extrabold tracking-tight sm:justify-center md:justify-start"
        >
          <span class="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-600 text-white"
            >+</span
          >
          <span class="hidden md:inline">MedFlow</span>
        </a>
        <nav class="flex flex-1 flex-col gap-1 px-3" aria-label="Principal">
          @for (item of nav; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="bg-brand-50 text-brand-600"
              #rla="routerLinkActive"
              [routerLinkActiveOptions]="{ exact: item.exact }"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              [attr.title]="item.label"
              class="flex items-center gap-3 rounded-control px-3 py-3 text-sm font-semibold text-content-muted hover:bg-canvas sm:justify-center md:justify-start"
            >
              <svg
                class="size-5 shrink-0"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path [attr.d]="item.icon" />
              </svg>
              <span class="sm:sr-only md:not-sr-only">{{ item.label }}</span>
            </a>
          }
        </nav>
        <p class="hidden px-4 py-4 text-xs text-content-subtle md:block">
          Dados: BNAFAR / e-SUS AF
        </p>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header
          class="sticky top-0 z-10 flex items-center gap-2 border-b border-outline bg-surface px-4 py-3 sm:hidden"
        >
          <span class="grid size-8 place-items-center rounded-lg bg-brand-600 text-white">+</span>
          <span class="text-lg font-extrabold tracking-tight">MedFlow</span>
        </header>

        <main class="mx-auto w-full max-w-7xl flex-1 p-4 pb-24 sm:p-6 md:p-8">
          <router-outlet />
        </main>

        <footer class="hidden px-4 py-6 text-center text-xs text-content-subtle sm:block md:hidden">
          Dados: BNAFAR / e-SUS AF · identificadores CNES, CATMAT e RENAME
        </footer>
      </div>

      <nav
        class="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-outline bg-surface pb-[env(safe-area-inset-bottom)] sm:hidden"
        aria-label="Principal"
      >
        @for (item of nav; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="text-brand-600"
            #rla="routerLinkActive"
            [routerLinkActiveOptions]="{ exact: item.exact }"
            [attr.aria-current]="rla.isActive ? 'page' : null"
            class="flex min-w-0 flex-col items-center gap-1 px-1 py-2 text-[0.6875rem] font-semibold text-content-muted"
          >
            <svg
              class="size-6"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path [attr.d]="item.icon" />
            </svg>
            <span class="w-full truncate text-center">{{ item.label }}</span>
          </a>
        }
      </nav>
    </div>
  `,
})
export class Shell {
  protected readonly nav = NAV_ITEMS;
}
