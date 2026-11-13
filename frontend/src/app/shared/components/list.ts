import { Component, input } from '@angular/core';

/**
 * Lista em cartão com divisórias. Os itens são `<li>` projetados;
 * quando vazia, mostra `vazio`.
 */
@Component({
  selector: 'app-list',
  template: `
    <ul class="surface-list" [attr.aria-label]="rotulo()">
      <ng-content />
      @if (vazia()) {
        <li class="body-muted p-4">{{ vazio() }}</li>
      }
    </ul>
  `,
})
export class List {
  readonly rotulo = input<string | null>(null);
  readonly vazia = input(false);
  readonly vazio = input('Nenhum item encontrado.');
}
