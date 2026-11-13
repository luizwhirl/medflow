import { Component, input, model, output } from '@angular/core';

/** Campo de busca com ícone de lupa. `valor` é two-way; Enter emite `buscar`. */
@Component({
  selector: 'app-search-field',
  template: `
    <label class="relative block">
      <span class="sr-only">{{ rotulo() }}</span>
      <svg
        class="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-content-subtle"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5" />
      </svg>
      <input
        type="search"
        class="control w-full pl-12"
        [placeholder]="placeholder()"
        [value]="valor()"
        (input)="valor.set($any($event.target).value)"
        (keydown.enter)="$event.preventDefault(); buscar.emit(valor())"
      />
    </label>
  `,
})
export class SearchField {
  readonly valor = model('');
  readonly placeholder = input('Buscar remédio');
  readonly rotulo = input('Buscar medicamento');
  readonly buscar = output<string>();
}
