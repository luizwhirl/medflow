import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { ItemAtencao, PainelGestao, SituacaoItem } from '../../core/models/gestao.model';
import { GestaoService } from '../../core/services/gestao.service';
import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';
import { Chip } from '../../shared/components/chip';

type Estado = { carregando: true } | { carregando: false; painel: PainelGestao | null };
type Filtro = 'TODOS' | 'EM_FALTA' | 'ABAIXO_MINIMO';

const FILTROS: readonly { valor: Filtro; rotulo: string }[] = [
  { valor: 'TODOS', rotulo: 'Todos' },
  { valor: 'EM_FALTA', rotulo: 'Em falta' },
  { valor: 'ABAIXO_MINIMO', rotulo: 'Abaixo do mínimo' },
];

const SITUACAO_CLASSES: Record<SituacaoItem, string> = {
  EM_FALTA: 'bg-status-falta-subtle text-status-falta',
  ABAIXO_MINIMO: 'bg-status-alerta-subtle text-status-alerta',
  PEDIDO_ENVIADO: 'bg-status-info-subtle text-status-info',
};

/** Painel da gestão da farmácia (frame "Desktop · Painel da gestão"). */
@Component({
  selector: 'app-painel-gestao-page',
  imports: [RouterLink, Button, Card, Chip],
  template: `
    @let e = estado();
    @if (e.carregando) {
      <p class="body-muted">Carregando…</p>
    } @else if (!e.painel) {
      <p class="feedback-error">Não foi possível carregar os indicadores da farmácia.</p>
    } @else {
      @let p = e.painel;
      @let k = p.indicadores;
      <!-- leading-[normal]: o Figma usa altura de linha "normal" em todo o frame. -->
      <div class="flex flex-col gap-6 leading-[normal]">
        <header class="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
          <div class="flex flex-col gap-1">
            <p class="text-sm text-content-muted">{{ p.ubs.nome }} · CNES {{ p.ubs.cnes }}</p>
            <h1 class="text-[1.75rem] font-bold text-content">Visão geral do estoque</h1>
          </div>
          <div class="flex flex-wrap items-center gap-2.5">
            <p class="text-[0.8125rem] text-content-muted">{{ textoSync() }}</p>
            <button
              appButton
              tamanho="sm"
              variante="secundario"
              type="button"
              (click)="exportar(p)"
            >
              Exportar
            </button>
            <a appButton tamanho="sm" routerLink="/gestao/estoque">Registrar entrada</a>
          </div>
        </header>

        <section class="grid grid-cols-2 gap-4 md:grid-cols-4" aria-label="Indicadores">
          <app-card pad="none" class="flex flex-col gap-1.5 px-5 py-[1.125rem]">
            <h2 class="kpi-rotulo">Itens em falta</h2>
            <p class="kpi-valor text-status-falta">{{ k.itensEmFalta }}</p>
            <p class="kpi-detalhe">{{ k.emFaltaComRemessa }} com remessa a caminho</p>
          </app-card>
          <app-card pad="none" class="flex flex-col gap-1.5 px-5 py-[1.125rem]">
            <h2 class="kpi-rotulo">Abaixo do mínimo</h2>
            <p class="kpi-valor text-status-alerta">{{ k.abaixoDoMinimo }}</p>
            <p class="kpi-detalhe">Repor nos próximos {{ k.prazoReposicaoDias }} dias</p>
          </app-card>
          <app-card pad="none" class="flex flex-col gap-1.5 px-5 py-[1.125rem]">
            <h2 class="kpi-rotulo">Remessas a caminho</h2>
            <p class="kpi-valor text-status-info">{{ k.remessasACaminho }}</p>
            <p class="kpi-detalhe">
              {{
                k.proximaRemessa ? 'Próxima: ' + dataCurta(k.proximaRemessa) : 'Nenhuma prevista'
              }}
            </p>
          </app-card>
          <app-card pad="none" class="flex flex-col gap-1.5 px-5 py-[1.125rem]">
            <h2 class="kpi-rotulo">Pacientes aguardando aviso</h2>
            <p class="kpi-valor text-content">{{ k.pacientesAguardando }}</p>
            <p class="kpi-detalhe">Em {{ k.medicamentosAguardados }} medicamentos</p>
          </app-card>
        </section>

        <div class="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_21.25rem]">
          <app-card pad="none" class="overflow-hidden">
            <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-[1.125rem]">
              <h2 class="text-[1.0625rem] font-bold text-content">Itens que pedem atenção</h2>
              <div class="flex flex-wrap gap-2" role="group" aria-label="Filtrar itens">
                @for (f of filtros; track f.valor) {
                  <app-chip
                    #chip
                    variante="filtro-sm"
                    [selecionado]="filtro() === f.valor"
                    (selecionadoChange)="filtrar(f.valor, $event, chip)"
                    >{{ f.rotulo }}</app-chip
                  >
                }
              </div>
            </div>

            <div class="overflow-x-auto">
              <table class="w-full min-w-[40rem] text-left text-sm">
                <thead
                  class="bg-surface-muted text-xs font-semibold uppercase tracking-[0.0375rem] text-content-muted"
                >
                  <tr>
                    <th scope="col" class="py-3 pl-5 font-semibold">Medicamento</th>
                    <!-- Larguras do Figma (70/70/130/190 px) + 12 px de espaçamento entre colunas. -->
                    <th scope="col" class="w-[5.125rem] py-3 pl-3 text-right font-semibold">
                      Estoque
                    </th>
                    <th scope="col" class="w-[5.125rem] py-3 pl-3 text-right font-semibold">
                      Mínimo
                    </th>
                    <th scope="col" class="w-[8.875rem] py-3 pl-3 text-right font-semibold">
                      Aguardando aviso
                    </th>
                    <th scope="col" class="w-[13.875rem] py-3 pl-3 pr-5 font-semibold">Situação</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of itensFiltrados(); track item.medicamentoId) {
                    <tr class="border-t border-canvas">
                      <th scope="row" class="py-3.5 pl-5 font-semibold text-content">
                        {{ item.medicamento }}
                      </th>
                      <td class="py-3.5 pl-3 text-right font-medium text-content">
                        {{ item.estoque }}
                      </td>
                      <td class="py-3.5 pl-3 text-right text-content-muted">{{ item.minimo }}</td>
                      <td class="py-3.5 pl-3 text-right font-medium text-content">
                        {{ item.aguardandoAviso }}
                      </td>
                      <td class="py-3.5 pl-3 pr-5">
                        <span
                          class="inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-[0.8125rem] font-semibold"
                          [class]="situacaoClasses[item.situacao]"
                          >{{ situacaoTexto(item) }}</span
                        >
                      </td>
                    </tr>
                  } @empty {
                    <tr class="border-t border-canvas">
                      <td colspan="5" class="body-muted px-5 py-4">Nenhum item nesta situação.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </app-card>

          <app-card pad="none" class="flex flex-col gap-4 px-5 py-[1.125rem]">
            <div class="flex flex-col gap-0.5">
              <h2 class="text-[1.0625rem] font-bold text-content">Mais procurados no app</h2>
              <p class="text-[0.8125rem] text-content-muted">
                Buscas feitas perto desta UBS, últimos 7 dias
              </p>
            </div>
            <ul class="flex flex-col gap-4">
              @for (m of p.maisProcurados; track m.medicamento) {
                <li class="flex flex-col gap-1.5">
                  <div class="flex justify-between gap-3 text-sm text-content">
                    <span>{{ m.medicamento }}</span>
                    <span class="font-bold">{{ m.buscas }}</span>
                  </div>
                  <div class="h-2.5 overflow-hidden rounded-full bg-canvas" aria-hidden="true">
                    <div
                      class="h-full rounded-full bg-brand-600"
                      [style.width.%]="(m.buscas / maxBuscas()) * 100"
                    ></div>
                  </div>
                </li>
              }
            </ul>
            @if (p.ficticio) {
              <p class="text-xs text-content-muted">Dados fictícios para demonstração.</p>
            }
          </app-card>
        </div>
      </div>
    }
  `,
  styles: `
    .kpi-rotulo {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--color-content-muted);
    }
    .kpi-valor {
      font-size: 2rem;
      font-weight: 700;
      line-height: normal;
    }
    .kpi-detalhe {
      font-size: 0.8125rem;
      color: var(--color-content-muted);
    }
  `,
})
export class PainelGestaoPage {
  private readonly gestao = inject(GestaoService);

  protected readonly filtros = FILTROS;
  protected readonly situacaoClasses = SITUACAO_CLASSES;
  protected readonly filtro = signal<Filtro>('TODOS');

  protected readonly estado = toSignal(
    this.gestao.painel().pipe(
      map((painel): Estado => ({ carregando: false, painel })),
      catchError(() => of<Estado>({ carregando: false, painel: null })),
    ),
    { initialValue: { carregando: true } as Estado },
  );

  private readonly painel = computed(() => {
    const e = this.estado();
    return e.carregando ? null : e.painel;
  });

  protected readonly itensFiltrados = computed(() => {
    const itens = this.painel()?.itensAtencao ?? [];
    const f = this.filtro();
    return f === 'TODOS' ? itens : itens.filter((i) => i.situacao === f);
  });

  protected readonly maxBuscas = computed(() =>
    Math.max(1, ...(this.painel()?.maisProcurados ?? []).map((m) => m.buscas)),
  );

  protected readonly textoSync = computed(() => {
    const em = this.painel()?.ultimaSincronizacao;
    if (!em) return 'Ainda não sincronizado com e-SUS AF';
    const min = Math.max(0, Math.round((Date.now() - new Date(em).getTime()) / 60_000));
    if (min < 60) return `Sincronizado com e-SUS AF há ${min} min`;
    if (min < 1440) return `Sincronizado com e-SUS AF há ${Math.round(min / 60)} h`;
    return `Sincronizado com e-SUS AF há ${Math.round(min / 1440)} dias`;
  });

  /** Filtros são exclusivos; desmarcar um volta para "Todos" (que não se desmarca). */
  protected filtrar(valor: Filtro, ligado: boolean, chip: Chip): void {
    if (valor === 'TODOS' || !ligado) {
      this.filtro.set('TODOS');
      if (valor === 'TODOS') chip.selecionado.set(true);
    } else {
      this.filtro.set(valor);
    }
  }

  protected situacaoTexto(item: ItemAtencao): string {
    switch (item.situacao) {
      case 'EM_FALTA':
        return item.remessaPrevista
          ? `Em falta · remessa ${diaDaSemana(item.remessaPrevista)}`
          : 'Em falta';
      case 'ABAIXO_MINIMO':
        return 'Abaixo do mínimo';
      case 'PEDIDO_ENVIADO':
        return 'Pedido enviado';
    }
  }

  /** "qui, 22/10" */
  protected dataCurta(iso: string): string {
    const d = dataLocal(iso);
    const dia = String(d.getDate()).padStart(2, '0');
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    return `${diaDaSemana(iso)}, ${dia}/${mes}`;
  }

  /** Baixa a tabela "Itens que pedem atenção" em CSV (abre no Excel/Planilhas). */
  protected exportar(p: PainelGestao): void {
    const linhas = [
      ['Medicamento', 'Estoque', 'Mínimo', 'Aguardando aviso', 'Situação'],
      ...p.itensAtencao.map((i) => [
        i.medicamento,
        String(i.estoque),
        String(i.minimo),
        String(i.aguardandoAviso),
        this.situacaoTexto(i),
      ]),
    ];
    const csv = linhas
      .map((l) => l.map((c) => `"${c.replaceAll('"', '""')}"`).join(';'))
      .join('\r\n');
    // BOM para o Excel reconhecer os acentos.
    const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `estoque-cnes-${p.ubs.cnes}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

/** `yyyy-MM-dd` como data local (new Date(iso) seria meia-noite UTC). */
function dataLocal(iso: string): Date {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

function diaDaSemana(iso: string): string {
  return DIAS[dataLocal(iso).getDay()];
}
