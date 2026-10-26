import { Component, computed, input } from '@angular/core';

import { STATUS_LABEL, StatusEstoque } from '../../core/models/status-estoque';

const CLASSES: Record<StatusEstoque, string> = {
  DISPONIVEL: 'bg-green-100 text-status-disponivel',
  RESERVADO: 'bg-amber-100 text-status-reservado',
  EM_FALTA: 'bg-red-100 text-status-falta',
};

@Component({
  selector: 'app-status-badge',
  template: `
    <span
      class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold"
      [class]="classes()"
    >
      <span class="size-1.5 rounded-full bg-current"></span>
      {{ label() }}
    </span>
  `,
})
export class StatusBadge {
  readonly status = input.required<StatusEstoque>();
  protected readonly label = computed(() => STATUS_LABEL[this.status()]);
  protected readonly classes = computed(() => CLASSES[this.status()]);
}
