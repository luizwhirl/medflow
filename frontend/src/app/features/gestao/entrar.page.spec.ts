import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { SessaoService } from '../../core/auth/sessao.service';
import { EntrarPage } from './entrar.page';

describe('EntrarPage', () => {
  let entrar: jasmine.Spy;
  let sair: jasmine.Spy;
  let navegar: jasmine.Spy;

  function montar(volta?: string): ComponentFixture<EntrarPage> {
    entrar = jasmine.createSpy('entrar');
    sair = jasmine.createSpy('sair');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessaoService, useValue: { entrar, sair } }],
    });
    navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const f = TestBed.createComponent(EntrarPage);
    if (volta) f.componentRef.setInput('volta', volta);
    f.detectChanges();
    return f;
  }

  function enviar(
    f: ComponentFixture<EntrarPage>,
    email = 'carlos@medflow.local',
    senha = 'x',
  ): void {
    const el = f.nativeElement as HTMLElement;
    const [campoEmail, campoSenha] = Array.from(el.querySelectorAll('input'));
    campoEmail.value = email;
    campoEmail.dispatchEvent(new Event('input'));
    campoSenha.value = senha;
    campoSenha.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  const erro = (f: ComponentFixture<EntrarPage>) =>
    (f.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent?.trim();

  it('gestor entra e volta para a página da gestão que pediu', () => {
    const f = montar('/gestao/remessas');
    entrar.and.returnValue(of({ papel: 'GESTOR' }));
    enviar(f, ' carlos@medflow.local ', 'medflow-dev-123');
    expect(entrar).toHaveBeenCalledWith('carlos@medflow.local', 'medflow-dev-123');
    expect(navegar).toHaveBeenCalledWith('/gestao/remessas');
  });

  it('não volta para fora da gestão', () => {
    const f = montar('https://exemplo.com');
    entrar.and.returnValue(of({ papel: 'GESTOR' }));
    enviar(f);
    expect(navegar).toHaveBeenCalledWith('/gestao');
  });

  it('conta de cidadão não entra na gestão', () => {
    const f = montar();
    entrar.and.returnValue(of({ papel: 'CIDADAO' }));
    enviar(f);
    expect(sair).toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(erro(f)).toContain('não é de gestor');
  });

  it('senha errada mostra o erro', () => {
    const f = montar();
    entrar.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    enviar(f);
    expect(erro(f)).toBe('E-mail ou senha incorretos.');
  });
});
