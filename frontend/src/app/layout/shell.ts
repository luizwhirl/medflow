import { NgTemplateOutlet } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ICONE_MARCA, NAV_ITEMS } from './nav-items';

/**
 * Casca da aplicação, conforme o Figma.
 * - Celular (< sm): barra de abas fixa no rodapé.
 * - Tablet retrato e paisagem (sm até xl): trilho lateral com ícone e rótulo curto.
 * - Desktop (>= xl): menu lateral com rótulos completos.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgTemplateOutlet],
  template: `
    <ng-template #icone let-d>
      <svg
        class="size-6 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path [attr.d]="d" />
      </svg>
    </ng-template>

    <div class="flex min-h-dvh bg-canvas">
      <aside
        class="sticky top-0 hidden h-dvh shrink-0 flex-col border-r border-outline bg-surface sm:flex sm:w-24 xl:w-[17rem]"
      >
        <a
          routerLink="/"
          class="flex items-center gap-3 px-4 pb-6 pt-8 sm:justify-center xl:justify-start xl:px-8"
          aria-label="UBS Conecta, início"
        >
          <span
            class="grid size-12 shrink-0 place-items-center rounded-control bg-brand-600 text-white xl:size-10"
          >
            <svg
              class="size-6"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path [attr.d]="iconeMarca" />
            </svg>
          </span>
          <span class="hidden text-xl font-bold tracking-tight xl:inline">UBS Conecta</span>
        </a>

        <nav class="flex flex-1 flex-col gap-2 px-2 xl:gap-1 xl:px-4" aria-label="Principal">
          @for (item of nav; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="ativo"
              #rla="routerLinkActive"
              [routerLinkActiveOptions]="{ exact: item.exact }"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              class="group flex flex-col items-center gap-1 py-1 text-xs font-medium text-content-muted xl:flex-row xl:gap-3 xl:rounded-control xl:px-4 xl:py-3 xl:text-base xl:font-normal xl:text-content [&.ativo]:text-brand-600 xl:[&.ativo]:bg-brand-50 xl:[&.ativo]:font-semibold"
            >
              <span
                class="grid h-10 w-16 place-items-center rounded-full group-[.ativo]:bg-brand-100 xl:h-auto xl:w-auto xl:bg-transparent! xl:text-content-muted xl:group-[.ativo]:text-brand-600"
              >
                <ng-container *ngTemplateOutlet="icone; context: { $implicit: item.icon }" />
              </span>
              <span class="xl:hidden">{{ item.curto }}</span>
              <span class="hidden xl:inline">{{ item.label }}</span>
            </a>
          }
        </nav>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header
          class="sticky top-0 z-10 flex items-center gap-2 border-b border-outline bg-surface px-4 py-3 sm:hidden"
        >
          <span class="grid size-8 place-items-center rounded-lg bg-brand-600 text-white">
            <svg
              class="size-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path [attr.d]="iconeMarca" />
            </svg>
          </span>
          <span class="text-lg font-bold tracking-tight">UBS Conecta</span>
        </header>

        <main
          class="mx-auto w-full max-w-[100rem] flex-1 p-4 pb-24 sm:p-8 sm:pb-8 xl:px-14 xl:py-8"
        >
          <router-outlet />
        </main>
      </div>

      <nav
        class="fixed inset-x-0 bottom-0 z-[1000] grid grid-cols-6 border-t border-outline bg-surface pb-[env(safe-area-inset-bottom)] sm:hidden"
        aria-label="Principal"
      >
        @for (item of nav; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="text-brand-600"
            #rla="routerLinkActive"
            [routerLinkActiveOptions]="{ exact: item.exact }"
            [attr.aria-current]="rla.isActive ? 'page' : null"
            class="flex min-w-0 flex-col items-center gap-0.5 px-0.5 py-2 text-[0.625rem] font-medium text-content-muted"
          >
            <ng-container *ngTemplateOutlet="icone; context: { $implicit: item.icon }" />
            <span class="w-full truncate text-center">{{ item.curto }}</span>
          </a>
        }
      </nav>
    </div>
  `,
})
export class Shell {
  protected readonly nav = NAV_ITEMS;
  protected readonly iconeMarca = ICONE_MARCA;
}
