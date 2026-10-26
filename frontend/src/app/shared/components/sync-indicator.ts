import { Component, computed, input } from '@angular/core';

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

  private readonly minutos = computed(() => {
    const em = this.em();
    return em ? Math.max(0, (Date.now() - new Date(em).getTime()) / 60_000) : null;
  });

  protected readonly texto = computed(() => {
    const min = this.minutos();
    if (min === null) return 'Nunca sincronizado';
    if (min < 60) return `Sincronizado há ${Math.round(min)} min`;
    if (min < 1440) return `Sincronizado há ${Math.round(min / 60)} h`;
    return `Sincronizado há ${Math.round(min / 1440)} dias`;
  });

  protected readonly tone = computed(() => {
    const min = this.minutos();
    if (min !== null && min <= 60) return { text: 'text-slate-600', dot: 'bg-green-600' };
    if (min !== null && min <= 1440) return { text: 'text-slate-600', dot: 'bg-amber-500' };
    return { text: 'text-status-falta font-semibold', dot: 'bg-red-600' };
  });
}
