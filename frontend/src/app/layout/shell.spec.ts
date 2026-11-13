import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Shell } from './shell';

describe('Shell', () => {
  it('renderiza as cinco abas na navegação inferior e na lateral', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const navs = (fixture.nativeElement as HTMLElement).querySelectorAll('nav');
    expect(navs.length).toBe(2);
    navs.forEach((nav) => {
      const rotulos = Array.from(nav.querySelectorAll('a')).map((a) => a.textContent?.trim());
      expect(rotulos).toEqual(['Início', 'Mapa', 'Buscar', 'Minha lista', 'Perfil']);
    });
  });
});
