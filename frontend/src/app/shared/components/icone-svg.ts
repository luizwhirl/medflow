import { Component, computed, input } from '@angular/core';

/**
 * Ícone vindo de um SVG do Figma (em `public/icons`), pintado com a cor do texto.
 * O arquivo é usado como máscara, então `text-*` controla a cor (ativo/inativo)
 * sem editar o asset. A área do ícone é 20x20 e o desenho fica centralizado no
 * tamanho original do SVG, como no Figma.
 */
@Component({
  selector: 'app-icone-svg',
  host: { class: 'grid size-5 shrink-0 place-items-center', 'aria-hidden': 'true' },
  template: `<span class="block bg-current" [style]="estilo()"></span>`,
})
export class IconeSvg {
  readonly src = input.required<string>();
  readonly largura = input.required<number>();
  readonly altura = input.required<number>();

  protected readonly estilo = computed(() => {
    const mascara = `url(${this.src()}) center / 100% 100% no-repeat`;
    return {
      width: `${this.largura()}px`,
      height: `${this.altura()}px`,
      mask: mascara,
      '-webkit-mask': mascara,
    };
  });
}
