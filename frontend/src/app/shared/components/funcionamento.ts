import { Component, computed, input } from '@angular/core';

import { situacaoFuncionamento, TomFuncionamento } from '../../core/funcionamento';
import { Ubs } from '../../core/models/ubs.model';

const COR: Record<TomFuncionamento, string> = {
  aberta: 'text-status-disponivel',
  fechando: 'text-status-alerta',
  fechada: 'text-content-muted',
};

/**
 * "Aberta até 17h", "Fecha às 16h" ou "Fechada agora", com o ponto colorido.
 * Não mostra nada quando a UBS não tem horário cadastrado.
 */
@Component({
  selector: 'app-funcionamento',
  template: `
    @if (situacao(); as s) {
      <span
        class="inline-flex items-center gap-1.5 whitespace-nowrap font-semibold"
        [class]="cor[s.tom]"
        data-funcionamento
      >
        <span class="size-1.5 shrink-0 rounded-full bg-current" aria-hidden="true"></span>
        {{ s.texto }}
      </span>
    }
  `,
})
export class Funcionamento {
  readonly ubs = input.required<Pick<Ubs, 'abertaAgora' | 'fechaAs' | 'fechaEm'>>();
  protected readonly cor = COR;
  protected readonly situacao = computed(() => situacaoFuncionamento(this.ubs()));
}
