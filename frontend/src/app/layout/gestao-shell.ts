import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { BannerOffline } from '../shared/components/banner-offline';
import { IconeSvg } from '../shared/components/icone-svg';
import { GESTAO_NAV_ITEMS, ICONE_MARCA_GESTAO } from './gestao-nav-items';

/**
 * Casca da área de gestão da farmácia (frame "Desktop · Painel da gestão").
 * - Desktop (>= xl): menu escuro de 248 px com rótulos completos e o servidor logado.
 * - Tablet (sm até xl): trilho escuro com ícone e rótulo curto.
 * - Celular (< sm): barra escura no topo com as abas roláveis.
 */
@Component({
  selector: 'app-gestao-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconeSvg, BannerOffline],
  template: `
    <div class="flex min-h-dvh flex-col bg-canvas sm:flex-row">
      <aside
        class="sticky top-0 z-10 flex shrink-0 flex-col bg-sidebar text-sidebar-texto sm:h-dvh sm:w-24 sm:px-2 sm:py-6 xl:w-[15.5rem] xl:gap-6 xl:px-4"
      >
        <a
          routerLink="/gestao"
          class="flex items-center gap-2.5 px-4 pt-3 sm:justify-center sm:px-0 sm:pt-0 xl:justify-start xl:px-2"
          aria-label="UBS Conecta, gestão da farmácia"
        >
          <span
            class="grid size-9 shrink-0 place-items-center rounded-control bg-brand-600 text-white"
          >
            <app-icone-svg [src]="marca.src" [largura]="marca.largura" [altura]="marca.altura" />
          </span>
          <span class="flex flex-col sm:hidden xl:flex">
            <span class="text-[1.0625rem] font-bold text-white">UBS Conecta</span>
            <span class="text-xs">Gestão da farmácia</span>
          </span>
        </a>

        <nav
          class="flex gap-1 overflow-x-auto px-2 py-3 sm:mt-6 sm:flex-col sm:gap-2 sm:overflow-visible sm:p-0 xl:mt-0 xl:gap-1"
          aria-label="Gestão"
        >
          @for (item of nav; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="ativo"
              #rla="routerLinkActive"
              [routerLinkActiveOptions]="{ exact: item.exact }"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              [attr.aria-label]="item.label"
              class="flex shrink-0 items-center gap-2 rounded-control px-3 py-2 text-sm font-medium transition-colors hover:bg-sidebar-ativo hover:text-white sm:flex-col sm:gap-1 sm:px-1 sm:py-2 sm:text-xs xl:h-11 xl:flex-row xl:gap-3 xl:px-3 xl:py-0 xl:text-[0.9375rem] [&.ativo]:bg-sidebar-ativo [&.ativo]:font-semibold [&.ativo]:text-white"
            >
              <app-icone-svg
                [src]="item.icone.src"
                [largura]="item.icone.largura"
                [altura]="item.icone.altura"
              />
              <span class="whitespace-nowrap xl:hidden">{{ item.curto }}</span>
              <span class="hidden whitespace-nowrap xl:inline">{{ item.label }}</span>
            </a>
          }
        </nav>

        <div class="hidden flex-1 xl:block"></div>

        <div class="hidden rounded-xl bg-sidebar-ativo p-3 text-[0.8125rem] xl:block">
          <p class="font-semibold text-white">{{ servidor.nome }}</p>
          <p class="truncate">{{ servidor.lotacao }}</p>
        </div>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <app-banner-offline />
        <main
          class="mx-auto w-full min-w-0 max-w-[100rem] flex-1 px-4 pb-12 pt-6 sm:px-8 sm:pt-8 xl:px-10"
        >
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class GestaoShell {
  protected readonly nav = GESTAO_NAV_ITEMS;
  protected readonly marca = ICONE_MARCA_GESTAO;
  // TODO(L6): vir do usuário autenticado (papel GESTOR).
  protected readonly servidor = { nome: 'Seu Carlos', lotacao: 'Farmácia · UBS Benedito Bentes I' };
}
