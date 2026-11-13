import { Component, input, model, output } from '@angular/core';

/**
 * Campo de busca com ícone de lupa. `valor` é two-way; Enter emite `buscar`.
 * Com `rotuloVisivel`, o rótulo aparece acima do campo (como no Figma).
 */
@Component({
  selector: 'app-search-field',
  template: `
    <label class="block">
      <span
        [class]="rotuloVisivel() ? 'mb-2 block text-sm font-semibold text-content' : 'sr-only'"
        >{{ rotulo() }}</span
      >
      <span class="relative block">
        <svg
          class="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-content-muted"
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
          class="control h-12 w-full pl-12"
          [placeholder]="placeholder()"
          [value]="valor()"
          (input)="valor.set($any($event.target).value)"
          (keydown.enter)="$event.preventDefault(); buscar.emit(valor())"
        />
      </span>
    </label>
  `,
})
export class SearchField {
  readonly valor = model('');
  readonly placeholder = input('Buscar remédio');
  readonly rotulo = input('Buscar medicamento');
  readonly rotuloVisivel = input(false);
  readonly buscar = output<string>();
}
