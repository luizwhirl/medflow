import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { SessaoService } from '../core/auth/sessao.service';
import { GestaoShell } from './gestao-shell';

describe('GestaoShell', () => {
  let http: HttpTestingController;

  function montar(): ComponentFixture<GestaoShell> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(GestaoShell);
    fixture.detectChanges();
    return fixture;
  }

  const aside = (f: ComponentFixture<GestaoShell>) =>
    (f.nativeElement as HTMLElement).querySelector('aside')!;

  it('menu tem os rótulos completos do Figma e os curtos do tablet', () => {
    const links = Array.from(aside(montar()).querySelectorAll('nav a'));
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

  it('mostra no rodapé do menu o gestor logado e a UBS dele, vindos da API', () => {
    const f = montar();
    http
      .expectOne('/api/auth/eu')
      .flush({ id: 2, nome: 'Seu Carlos', email: 'c@x', papel: 'GESTOR' });
    http
      .expectOne('/api/gestao/escopo')
      .flush({ ubs: { id: 1, nome: 'US Denisson Menezes', cnes: '2003317' }, restrito: true });
    f.detectChanges();
    expect(aside(f).textContent).toContain('Seu Carlos');
    expect(aside(f).textContent).toContain('Farmácia · US Denisson Menezes');
  });

  it('gestor sem vínculo aparece como da rede municipal', () => {
    const f = montar();
    http
      .expectOne('/api/auth/eu')
      .flush({ id: 3, nome: 'Dona Marta', email: 'm@x', papel: 'GESTOR' });
    http.expectOne('/api/gestao/escopo').flush({ ubs: null, restrito: false });
    f.detectChanges();
    expect(aside(f).textContent).toContain('Rede municipal');
  });

  it('"Sair" encerra a sessão e volta para a tela de entrada', () => {
    const f = montar();
    const sair = spyOn(TestBed.inject(SessaoService), 'sair');
    const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    Array.from(aside(f).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Sair')!
      .click();
    expect(sair).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith('/gestao/entrar');
  });
});
