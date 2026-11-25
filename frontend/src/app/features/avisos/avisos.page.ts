import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { quandoAconteceu, tomDoAviso, TomAviso } from '../../core/models/aviso.model';
import { AvisosService } from '../../core/services/avisos.service';
import { Chip } from '../../shared/components/chip';

const ICONE: Record<TomAviso, string> = {
  disponivel: 'M5 12.5 9.5 17 19 7',
  alerta: 'M12 3 2 20h20zM12 10v4M12 17h.01',
};

const COR: Record<TomAviso, string> = {
  disponivel: 'bg-status-disponivel-subtle text-status-disponivel',
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
            Chegadas dos remédios da sua lista e atrasos nos dados das UBS que você acompanha.
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
              >Não lidos · {{ e.naoLidos }}</app-chip
            >
          </div>

          <ul class="surface-list mt-4" aria-label="Lista de avisos">
            @for (l of linhas(); track l.id) {
              <li>
                <div
                  class="flex gap-4 px-5 py-4"
                  [class.bg-brand-50]="!l.lido"
                  [attr.data-lido]="l.lido"
                >
                  <span
                    class="grid size-11 shrink-0 place-items-center rounded-full"
                    [class]="cor[l.tom]"
                  >
                    <ng-container
                      *ngTemplateOutlet="icone; context: { $implicit: icones[l.tom] }"
                    />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="flex items-start gap-2">
                      <span
                        class="flex-1"
                        [class.font-bold]="!l.lido"
                        [class.font-semibold]="l.lido"
                      >
                        {{ l.titulo }}
                      </span>
                      @if (!l.lido) {
                        <span
                          class="mt-2 size-2.5 shrink-0 rounded-full bg-brand-600"
                          aria-hidden="true"
                        ></span>
                        <span class="sr-only">(não lido)</span>
                      }
                    </span>
                    <span class="mt-0.5 block text-sm text-content-muted">{{ l.texto }}</span>
                    @if (l.quando) {
                      <span class="mt-1 block text-xs text-content-muted">{{ l.quando }}</span>
                    }
                  </span>
                </div>
              </li>
            } @empty {
              <li class="body-muted p-6 text-center">
                @if (soNaoLidos()) {
                  Você leu todos os avisos.
                } @else {
                  Nenhum aviso agora. Ative o aviso nos remédios da
                  <a routerLink="/lista" class="font-semibold text-brand-600 hover:underline"
                    >Minha lista</a
                  >
                  para saber quando eles chegarem na UBS.
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

  protected readonly icones = ICONE;
  protected readonly cor = COR;
  protected readonly soNaoLidos = signal(false);

  protected readonly linhas = computed(() =>
    this.avisos
      .avisos()
      .filter((a) => !this.soNaoLidos() || !a.lido)
      .map((a) => ({
        id: a.id,
        lido: a.lido,
        titulo: a.titulo,
        texto: a.texto,
        tom: tomDoAviso(a),
        quando: quandoAconteceu(a.criadoEm),
      })),
  );
}
