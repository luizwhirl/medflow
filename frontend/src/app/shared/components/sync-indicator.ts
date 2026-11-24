import { Component, computed, input } from '@angular/core';

import {
  minutosDesde,
  NivelSincronizacao,
  nivelSincronizacao,
  tempoDesde,
} from '../../core/sincronizacao';

const TOM: Record<NivelSincronizacao, { text: string; dot: string }> = {
  'em-dia': { text: 'text-content-muted', dot: 'bg-status-disponivel' },
  atrasada: { text: 'text-content-muted', dot: 'bg-status-reservado' },
  critica: { text: 'text-status-falta font-semibold', dot: 'bg-status-falta' },
};

/** Mostra há quanto tempo a unidade sincronizou (transparência sobre a atualização dos dados). */
@Component({
  selector: 'app-sync-indicator',
  template: `
    <span class="inline-flex items-center gap-1.5 text-xs" [class]="tone().text">
      <span class="size-1.5 rounded-full" [class]="tone().dot"></span>
      {{ texto() }}
    </span>
  `,
})
export class SyncIndicator {
  readonly em = input<string | null>(null);

  private readonly minutos = computed(() => minutosDesde(this.em()));

  protected readonly texto = computed(() => {
    const min = this.minutos();
    return min === null ? 'Nunca sincronizado' : `Sincronizado ${tempoDesde(min)}`;
  });

  protected readonly tone = computed(() => TOM[nivelSincronizacao(this.minutos())]);
}
