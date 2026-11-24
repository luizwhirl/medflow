import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { quandoAconteceu, textoDoAviso, TomAviso } from '../../core/models/aviso.model';
import { AvisosService } from '../../core/services/avisos.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { Chip } from '../../shared/components/chip';

const ICONE: Record<TomAviso, string> = {
  disponivel: 'M5 12.5 9.5 17 19 7',
  info: 'M3 6h11v10H3zM14 9h4l3 3v4h-7zM7 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  alerta: 'M12 3 2 20h20zM12 10v4M12 17h.01',
};

const COR: Record<TomAviso, string> = {
  disponivel: 'bg-status-disponivel-subtle text-status-disponivel',
  info: 'bg-status-info-subtle text-status-info',
  alerta: 'bg-status-alerta-subtle text-status-alerta',
};

@Component({
  selector: 'app-avisos-page',
  imports: [NgTemplateOutlet, RouterLink, Chip],
  template: `
    <ng-template #icone let-d>
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
        <path [attr.d]="d" />
      </svg>
    </ng-template>

    <div class="mx-auto max-w-3xl">
      <header class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 class="text-3xl font-bold tracking-tight">Avisos</h1>
          <p class="mt-1 text-content-muted">
            Chegadas, remessas e atrasos da sua UBS para os seus remédios de uso contínuo.
          </p>
        </div>
        @if (avisos.naoLidos() > 0) {
          <button
            type="button"
            class="font-semibold text-brand-600 hover:underline"
            (click)="avisos.marcarTodosComoLidos()"
          >
            Marcar todos como lidos
          </button>
        }
      </header>

      @let e = avisos.estado();
      @switch (e.tipo) {
        @case ('sem-ubs') {
          <div class="surface-card mt-6 p-8 text-center">
            <h2 class="text-lg font-bold">Escolha sua UBS de referência</h2>
            <p class="mt-1 text-content-muted">
              Os avisos são da unidade onde você retira seus remédios.
            </p>
            <a
              routerLink="/mapa"
              class="mt-5 inline-flex h-12 items-center rounded-control bg-brand-600 px-5 font-bold text-white hover:bg-brand-700"
              >Escolher UBS</a
            >
          </div>
        }
        @case ('carregando') {
          <p class="body-muted mt-6">Carregando avisos…</p>
        }
        @case ('erro') {
          <div class="feedback-error mt-6 flex flex-wrap items-center justify-between gap-3">
            <span>Não foi possível carregar os avisos agora. Verifique a conexão.</span>
            <button type="button" class="font-semibold underline" (click)="avisos.recarregar()">
              Tentar de novo
            </button>
          </div>
        }
        @case ('ok') {
          <div class="mt-5 flex gap-2" role="group" aria-label="Filtrar avisos">
            <app-chip
              [selecionado]="!soNaoLidos()"
              [fixo]="true"
              (selecionadoChange)="soNaoLidos.set(false)"
              >Todos · {{ avisos.avisos().length }}</app-chip
            >
            <app-chip
              [selecionado]="soNaoLidos()"
              [fixo]="true"
              (selecionadoChange)="soNaoLidos.set(true)"
              >Não lidos · {{ avisos.naoLidos() }}</app-chip
            >
          </div>

          <ul class="surface-list mt-4" aria-label="Lista de avisos">
            @for (l of linhas(); track l.id) {
              <li>
                <a
                  [routerLink]="l.texto.link"
                  (click)="avisos.marcarComoLido(l.id)"
                  class="flex gap-4 px-5 py-4 transition-colors hover:bg-surface-muted"
                  [class.bg-brand-50]="!l.lido"
                  [attr.data-lido]="l.lido"
                >
                  <span
                    class="grid size-11 shrink-0 place-items-center rounded-full"
                    [class]="cor[l.texto.tom]"
                  >
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones[l.texto.tom] }"
                    />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="flex items-start gap-2">
                      <span
                        class="flex-1"
                        [class.font-bold]="!l.lido"
                        [class.font-semibold]="l.lido"
                      >
                        {{ l.texto.titulo }}
                      </span>
                      @if (!l.lido) {
                        <span
                          class="mt-2 size-2.5 shrink-0 rounded-full bg-brand-600"
                          aria-hidden="true"
                        ></span>
                        <span class="sr-only">(não lido)</span>
                      }
                    </span>
                    <span class="mt-0.5 block text-sm text-content-muted">{{
                      l.texto.detalhe
                    }}</span>
                    @if (l.quando) {
                      <span class="mt-1 block text-xs text-content-muted">{{ l.quando }}</span>
                    }
                  </span>
                </a>
              </li>
            } @empty {
              <li class="body-muted p-6 text-center">
                @if (soNaoLidos()) {
                  Você leu todos os avisos.
                } @else if (semRemedios()) {
                  Nenhum aviso agora. Adicione seus remédios de uso contínuo (em Buscar, “Adicionar
                  à lista”) para saber quando chegarem na sua UBS.
                } @else {
                  Nenhum aviso agora. Quando um remédio seu chegar ou estiver a caminho, ele aparece
                  aqui.
                }
              </li>
            }
          </ul>
        }
      }
    </div>
  `,
})
export class AvisosPage {
  protected readonly avisos = inject(AvisosService);
  private readonly preferencias = inject(PreferenciasService);

  protected readonly icones = ICONE;
  protected readonly cor = COR;
  protected readonly soNaoLidos = signal(false);
  protected readonly semRemedios = computed(
    () => this.preferencias.remediosContinuos().length === 0,
  );

  protected readonly linhas = computed(() =>
    this.avisos
      .avisos()
      .filter((a) => !this.soNaoLidos() || !a.lido)
      .map((a) => ({
        id: a.id,
        lido: a.lido,
        texto: textoDoAviso(a),
        quando: quandoAconteceu(a.ocorridoEm),
      })),
  );
}
