import { Component } from '@angular/core';

/** Miniatura decorativa de mapa com um pino (cabeçalho do cartão da UBS de referência). */
@Component({
  selector: 'app-mapa-ilustrado',
  host: { class: 'block overflow-hidden bg-brand-50', 'aria-hidden': 'true' },
  template: `
    <svg class="block h-full w-full" viewBox="0 0 400 150" preserveAspectRatio="xMidYMid slice">
      <g stroke="#ffffff" stroke-width="14" fill="none">
        <path d="M-10 48 L410 78" />
        <path d="M-10 122 L410 98" />
        <path d="M100 -10 L130 160" />
        <path d="M265 -10 L240 160" />
      </g>
      <circle cx="190" cy="82" r="10" fill="#0f766e" stroke="#ffffff" stroke-width="3" />
    </svg>
  `,
})
export class MapaIlustrado {}
