import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Shell } from './shell';

describe('Shell', () => {
  function montar(): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('menu lateral tem os rótulos completos e curtos das seis abas', () => {
    const lateral = montar().querySelector('aside nav')!;
    const completos = Array.from(lateral.querySelectorAll('a span.hidden')).map((s) =>
      s.textContent?.trim(),
    );
    expect(completos).toEqual([
      'Início',
      'Mapa de UBS',
      'Buscar medicamento',
      'Minha lista',
      'Avisos',
      'Perfil e modo offline',
    ]);
  });

  it('barra inferior usa os rótulos curtos', () => {
    const navs = montar().querySelectorAll('nav');
    const inferior = navs[navs.length - 1];
    const rotulos = Array.from(inferior.querySelectorAll('a')).map((a) => a.textContent?.trim());
    expect(rotulos).toEqual(['Início', 'Mapa', 'Buscar', 'Minha lista', 'Avisos', 'Perfil']);
  });
});
