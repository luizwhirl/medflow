import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { Coordenadas, distanciaKm, posicaoAtual } from '../../core/geo';
import { Disponibilidade } from '../../core/models/estoque.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { MedicamentoService } from '../../core/services/medicamento.service';
import { UbsService } from '../../core/services/ubs.service';
import { Chip } from '../../shared/components/chip';
import { List } from '../../shared/components/list';
import { Mapa, PinoMapa } from '../../shared/components/mapa';
import { StatusBadge } from '../../shared/components/status-badge';

export const RAIOS_KM = [1, 3, 6, 10] as const;

/** Cores dos pinos, iguais aos tokens `--color-status-*` / neutro. */
const COR_STATUS: Record<StatusEstoque | 'SEM_DADO', string> = {
  DISPONIVEL: '#15803d',
  RESERVADO: '#b45309',
  EM_FALTA: '#b91c1c',
  SEM_DADO: '#94a3b8',
};

/** Sem medicamento escolhido, os pinos usam a cor da marca. */
const COR_NEUTRA = '#0f766e';

interface LinhaUbs {
  ubs: Ubs;
  distancia: number | null;
  status: StatusEstoque | null;
  quantidade: number | null;
}

@Component({
  selector: 'app-ubs-map-page',
  imports: [RouterLink, Mapa, Chip, List, StatusBadge],
  template: `
    <h1 class="page-title mb-3">Mapa de UBS</h1>

    <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
      <label class="flex min-w-0 flex-1 items-center gap-2">
        <span class="text-sm font-semibold text-content-muted">Medicamento</span>
        <select
          class="control min-w-0 flex-1"
          [value]="medicamentoId() ?? ''"
          (change)="escolherMedicamento($any($event.target).value)"
        >
          <option value="">Todos (sem filtro)</option>
          @for (m of medicamentos(); track m.id) {
            <option [value]="m.id">{{ m.nome }} {{ m.concentracao }}</option>
          }
        </select>
      </label>
      <div class="flex gap-2 overflow-x-auto" role="group" aria-label="Raio de busca">
        @for (r of raios; track r) {
          <app-chip
            [selecionado]="raioKm() === r"
            (selecionadoChange)="raioKm.set($event ? r : null)"
            >{{ r }} km</app-chip
          >
        }
      </div>
    </div>
    @if (!posicao()) {
      <p class="mt-2 text-xs text-content-subtle">
        Permita a localização para filtrar por raio e ver as distâncias.
      </p>
    }

    <div class="mt-4 grid gap-4 md:grid-cols-[minmax(0,1fr)_22rem]">
      <app-mapa
        class="h-72 overflow-hidden rounded-card shadow-card sm:h-96 md:h-[calc(100dvh-14rem)] md:min-h-[28rem]"
        [pinos]="pinos()"
        [centro]="posicao()"
        [raioKm]="posicao() ? raioKm() : null"
        [selecionado]="selecionado()"
        (selecionar)="selecionado.set($event)"
      />

      <div class="min-w-0 md:max-h-[calc(100dvh-14rem)] md:overflow-y-auto">
        <app-list
          rotulo="Unidades no mapa"
          [vazia]="linhas().length === 0"
          vazio="Nenhuma unidade neste raio."
        >
          @for (l of linhas(); track l.ubs.id) {
            <li [class.bg-brand-50]="selecionado() === l.ubs.id">
              <a
                [routerLink]="['/ubs', l.ubs.id]"
                (mouseenter)="selecionado.set(l.ubs.id)"
                (focus)="selecionado.set(l.ubs.id)"
                class="interactive-row flex items-center gap-3 px-4 py-3"
              >
                <div class="min-w-0 flex-1">
                  <div class="truncate font-semibold">{{ l.ubs.nome }}</div>
                  <div class="body-muted">
                    {{ l.ubs.bairro }}
                    @if (l.distancia !== null) {
                      · {{ l.distancia.toFixed(1) }} km
                    }
                    @if (l.quantidade !== null) {
                      · {{ l.quantidade }} un.
                    }
                  </div>
                </div>
                @if (l.status) {
                  <app-status-badge [status]="l.status" />
                }
              </a>
            </li>
          }
        </app-list>
      </div>
    </div>
  `,
})
export class UbsMapPage {
  private readonly ubsService = inject(UbsService);
  private readonly medicamentoService = inject(MedicamentoService);

  protected readonly raios = RAIOS_KM;
  protected readonly raioKm = signal<number | null>(6);
  protected readonly medicamentoId = signal<number | null>(null);
  protected readonly selecionado = signal<number | null>(null);
  protected readonly posicao = signal<Coordenadas | null>(null);

  private readonly unidades = toSignal(this.ubsService.listar().pipe(catchError(() => of([]))), {
    initialValue: [] as Ubs[],
  });

  protected readonly medicamentos = toSignal(
    this.medicamentoService.buscar().pipe(catchError(() => of([]))),
    { initialValue: [] as Medicamento[] },
  );

  private readonly disponibilidade = toSignal(
    toObservable(this.medicamentoId).pipe(
      switchMap((id) =>
        id === null
          ? of(null)
          : this.medicamentoService.disponibilidade(id).pipe(
              map((d) => new Map(d.map((x) => [x.ubs.id, x] as const))),
              catchError(() => of(new Map<number, Disponibilidade>())),
            ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly linhas = computed((): LinhaUbs[] => {
    const pos = this.posicao();
    const raio = this.raioKm();
    const disp = this.disponibilidade();
    return this.unidades()
      .map((ubs) => {
        const d = disp?.get(ubs.id);
        return {
          ubs,
          distancia: pos ? distanciaKm(pos, ubs) : null,
          status: d?.status ?? null,
          quantidade: d?.quantidade ?? null,
        };
      })
      .filter((l) => l.distancia === null || raio === null || l.distancia <= raio)
      .sort((a, b) =>
        a.distancia !== null && b.distancia !== null
          ? a.distancia - b.distancia
          : a.ubs.nome.localeCompare(b.ubs.nome),
      );
  });

  protected readonly pinos = computed((): PinoMapa[] => {
    const comFiltro = this.medicamentoId() !== null;
    return this.linhas().map((l) => ({
      id: l.ubs.id,
      latitude: l.ubs.latitude,
      longitude: l.ubs.longitude,
      cor: comFiltro ? COR_STATUS[l.status ?? 'SEM_DADO'] : COR_NEUTRA,
      rotulo: l.ubs.nome,
    }));
  });

  constructor() {
    posicaoAtual().then((p) => this.posicao.set(p));
  }

  protected escolherMedicamento(valor: string): void {
    this.medicamentoId.set(valor ? Number(valor) : null);
  }
}
