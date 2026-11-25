import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';

import { destinoSeguro } from '../../core/guards/boas-vindas.guard';
import { posicaoAtual } from '../../core/geo';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsService } from '../../core/services/ubs.service';
import { Button } from '../../shared/components/button';

const ICONE = {
  marca:
    'M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  mapa: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14',
  caminhao:
    'M3 6h11v10H3zM14 9h4l3 3v4h-7zM7 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  marcador: 'M6 3h12v18l-6-4-6 4z',
  mira: 'M12 19a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2v3M12 19v3M2 12h3M19 12h3',
} as const;

const RECURSOS = [
  {
    icone: ICONE.mapa,
    titulo: 'UBS perto de você',
    texto: 'Estoque de cada unidade: disponível, reservado ou em falta.',
  },
  {
    icone: ICONE.caminhao,
    titulo: 'Acompanhe a chegada',
    texto: 'Veja se a remessa do seu remédio já saiu da central.',
  },
  {
    icone: ICONE.marcador,
    titulo: 'Avisos da sua UBS',
    texto: 'Saiba quando o remédio que você usa chegar na sua unidade.',
  },
] as const;

/** Primeira visita: o cidadão escolhe como o app acha a UBS dele (localização ou bairro). */
@Component({
  selector: 'app-boas-vindas-page',
  imports: [NgTemplateOutlet, Button],
  template: `
    <ng-template #icone let-d>
      <svg
        class="size-6 shrink-0"
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

    <main class="min-h-dvh bg-canvas px-4 py-8 sm:px-8 sm:py-12 xl:grid xl:place-items-center">
      <div
        class="mx-auto flex max-w-xl flex-col gap-8 xl:grid xl:max-w-5xl xl:grid-cols-[minmax(0,1fr)_26rem] xl:items-center xl:gap-14"
      >
        <section aria-labelledby="titulo-boas-vindas">
          <span
            class="grid size-14 place-items-center rounded-control bg-brand-600 text-white"
            aria-hidden="true"
          >
            <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.marca }" />
          </span>
          <p class="mt-6 font-semibold text-brand-600">UBS Conecta</p>
          <h1 id="titulo-boas-vindas" class="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
            Saiba se o remédio está na UBS
            <span class="text-brand-600">antes de sair de casa</span>.
          </h1>
          <p class="mt-3 text-lg text-content-muted">
            Consulte o estoque das unidades de saúde perto de você e acompanhe a chegada do seu
            medicamento.
          </p>
          <ul class="mt-6 flex flex-col gap-4">
            @for (r of recursos; track r.titulo) {
              <li class="flex gap-4">
                <span
                  class="grid size-12 shrink-0 place-items-center rounded-control bg-brand-50 text-brand-600"
                >
                  <ng-container *ngTemplateOutlet="icone; context: { $implicit: r.icone }" />
                </span>
                <span>
                  <b class="block">{{ r.titulo }}</b>
                  <span class="text-content-muted">{{ r.texto }}</span>
                </span>
              </li>
            }
          </ul>
        </section>

        <section
          aria-label="Como encontrar sua UBS"
          class="surface-card flex flex-col gap-5 p-6 sm:p-7"
        >
          <button
            appButton
            type="button"
            class="w-full"
            [disabled]="ocupado()"
            (click)="usarLocalizacao()"
          >
            <ng-container *ngTemplateOutlet="icone; context: { $implicit: icones.mira }" />
            {{
              ocupado() === 'localizacao'
                ? 'Procurando a UBS mais perto…'
                : 'Usar minha localização'
            }}
          </button>

          <div class="flex items-center gap-3 text-sm text-content-muted" aria-hidden="true">
            <span class="h-px flex-1 bg-outline"></span>ou<span
              class="h-px flex-1 bg-outline"
            ></span>
          </div>

          <form class="flex flex-col gap-2" (submit)="$event.preventDefault(); usarBairro()">
            <label for="bairro" class="text-sm font-semibold">Escolha seu bairro</label>
            <div class="flex gap-3">
              <select
                id="bairro"
                class="control min-w-0 flex-1"
                [value]="bairro()"
                (change)="bairro.set($any($event.target).value)"
                [disabled]="bairros() === null || erroBairros()"
                [attr.aria-describedby]="erroBairros() ? 'erro-bairros' : null"
              >
                <option value="" disabled>
                  {{ bairros() === null ? 'Carregando bairros…' : 'Selecione' }}
                </option>
                @for (b of bairros() ?? []; track b) {
                  <option [value]="b">{{ b }}</option>
                }
              </select>
              <button
                appButton
                variante="secundario"
                type="submit"
                [disabled]="!bairro() || ocupado()"
              >
                OK
              </button>
            </div>
            @if (erroBairros()) {
              <p id="erro-bairros" class="text-sm font-semibold text-status-falta">
                Não foi possível carregar os bairros. Use sua localização ou entre sem escolher.
              </p>
            }
          </form>

          @if (mensagem(); as m) {
            <p class="feedback-error" role="alert">{{ m }}</p>
          }

          <p class="text-center text-xs text-content-muted">
            Sua localização é usada só para encontrar as UBS mais perto de você.
          </p>
          <button
            type="button"
            class="mx-auto text-sm font-semibold text-brand-600 hover:underline"
            [disabled]="ocupado()"
            (click)="pular()"
          >
            Agora não
          </button>
        </section>
      </div>
    </main>
  `,
})
export class BoasVindasPage {
  private readonly ubsService = inject(UbsService);
  private readonly preferencias = inject(PreferenciasService);
  private readonly router = inject(Router);

  protected readonly icones = ICONE;
  protected readonly recursos = RECURSOS;

  /** Rota que o cidadão tinha aberto antes de cair aqui (query param `voltar`). */
  readonly voltar = input<string | undefined>();

  protected readonly bairro = signal('');
  protected readonly ocupado = signal<'localizacao' | 'bairro' | null>(null);
  protected readonly mensagem = signal<string | null>(null);

  /** `null` enquanto carrega; `'erro'` se a API falhou. */
  private readonly resposta = toSignal(
    this.ubsService.bairros().pipe(catchError(() => of('erro' as const))),
    { initialValue: null },
  );
  protected readonly erroBairros = computed(() => this.resposta() === 'erro');
  protected readonly bairros = computed(() => {
    const r = this.resposta();
    return r === 'erro' ? [] : r;
  });

  /** A UBS mais próxima vira a de referência (a busca por proximidade já vem ordenada). */
  protected async usarLocalizacao(): Promise<void> {
    this.iniciar('localizacao');
    const pos = await posicaoAtual();
    if (!pos) {
      return this.falhar('Não foi possível usar sua localização. Escolha seu bairro abaixo.');
    }
    try {
      const [maisPerto] = await firstValueFrom(this.ubsService.proximas(pos));
      if (!maisPerto) {
        return this.falhar('Nenhuma UBS a até 20 km de você. Escolha seu bairro abaixo.');
      }
      this.concluir(maisPerto.id);
    } catch {
      this.falhar('Não foi possível buscar as UBS agora. Tente de novo.');
    }
  }

  /** A primeira UBS do bairro (em ordem de nome) vira a de referência; dá para trocar no Mapa. */
  protected async usarBairro(): Promise<void> {
    const escolhido = this.bairro();
    if (!escolhido) return;
    this.iniciar('bairro');
    try {
      const ubs = (await firstValueFrom(this.ubsService.listar())).find(
        (u) => u.bairro === escolhido,
      );
      if (!ubs) return this.falhar('Não encontramos UBS nesse bairro. Escolha outro.');
      this.concluir(ubs.id);
    } catch {
      this.falhar('Não foi possível buscar as UBS agora. Tente de novo.');
    }
  }

  protected pular(): void {
    this.concluir(null);
  }

  private iniciar(acao: 'localizacao' | 'bairro'): void {
    this.mensagem.set(null);
    this.ocupado.set(acao);
  }

  private falhar(mensagem: string): void {
    this.mensagem.set(mensagem);
    this.ocupado.set(null);
  }

  private concluir(ubsReferenciaId: number | null): void {
    if (ubsReferenciaId !== null) this.preferencias.definirUbsReferencia(ubsReferenciaId);
    this.preferencias.concluirBoasVindas();
    this.router.navigateByUrl(destinoSeguro(this.voltar()), { replaceUrl: true });
  }
}
