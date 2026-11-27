import { Component, input, numberAttribute } from '@angular/core';

import { RastreioMedicamento } from './rastreio-medicamento';

@Component({
  selector: 'app-medicamento-detail-page',
  imports: [RastreioMedicamento],
  template: `<app-rastreio-medicamento [id]="id()" />`,
})
export class MedicamentoDetailPage {
  /** Vem do parâmetro de rota `:id` (withComponentInputBinding). */
  readonly id = input.required({ transform: numberAttribute });
}
