import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GestaoShell } from './gestao-shell';

describe('GestaoShell', () => {
  function montar(): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(GestaoShell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('menu tem os rótulos completos do Figma e os curtos do tablet', () => {
    const links = Array.from(montar().querySelectorAll('nav a'));
    expect(links.map((a) => a.querySelector('span.hidden')?.textContent?.trim())).toEqual([
      'Visão geral',
      'Estoque',
      'Remessas',
      'Demanda dos pacientes',
    ]);
    expect(links.map((a) => a.querySelector('span.xl\\:hidden')?.textContent?.trim())).toEqual([
      'Visão geral',
      'Estoque',
      'Remessas',
      'Demanda',
    ]);
  });

  it('mostra o servidor da farmácia no rodapé do menu', () => {
    expect(montar().querySelector('aside')?.textContent).toContain('Seu Carlos');
  });
});
