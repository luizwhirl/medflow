import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Shell } from './shell';

describe('Shell', () => {
  function montar(ubsReferenciaId: number | null = null) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId, remediosContinuos: [4], buscasRecentes: [] }),
    );
    localStorage.removeItem('medflow.avisos-lidos');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => localStorage.removeItem('medflow.preferencias'));

  it('menu lateral tem os rótulos completos e curtos das seis abas', () => {
    const lateral = montar().el.querySelector('aside nav')!;
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
    const navs = montar().el.querySelectorAll('nav');
    const inferior = navs[navs.length - 1];
    const rotulos = Array.from(inferior.querySelectorAll('a')).map((a) => a.textContent?.trim());
    expect(rotulos).toEqual(['Início', 'Mapa', 'Buscar', 'Minha lista', 'Avisos', 'Perfil']);
  });

  it('mostra quantos avisos não foram lidos na aba Avisos', () => {
    const { fixture, el } = montar(2);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=4').flush([
      { id: 'a', tipo: 'DISPONIVEL', ubsId: 2, ubsNome: 'UBS' },
      { id: 'b', tipo: 'SINCRONIZACAO_ATRASADA', ubsId: 2, ubsNome: 'UBS' },
    ]);
    fixture.detectChanges();

    const navs = el.querySelectorAll('nav');
    const avisos = Array.from(navs[navs.length - 1].querySelectorAll('a')).find((a) =>
      a.textContent!.includes('Avisos'),
    )!;
    // O número é só visual; o leitor de tela ouve "(2 não lidos)".
    expect(avisos.querySelector('span[aria-hidden="true"]')!.textContent!.trim()).toBe('2');
    expect(avisos.querySelector('.sr-only')!.textContent!.trim()).toBe('(2 não lidos)');
    http.verify();
  });
});
