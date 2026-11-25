import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Shell } from './shell';

describe('Shell', () => {
  function montar(naoLidos = 0): HTMLElement {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/avisos/nao-lidos/total').flush({ naoLidos });
    fixture.detectChanges();
    http.verify();
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

  it('mostra os não lidos na aba Avisos, no menu e na barra inferior', () => {
    const el = montar(120);
    const contadores = Array.from(el.querySelectorAll('[data-contador]'));
    expect(contadores.map((c) => c.textContent!.trim())).toEqual(['99+', '99+']);
    for (const c of contadores) {
      expect(c.closest('a')!.getAttribute('href')).toBe('/avisos');
      // o leitor de tela ouve o número exato
      expect(c.closest('a')!.textContent).toContain('120 não lidos');
    }
  });

  it('sem avisos não lidos, não mostra contador', () => {
    expect(montar(0).querySelector('[data-contador]')).toBeNull();
  });
});
