import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Esqueleto, FormatoEsqueleto } from './esqueleto';
import { EstadoVazio } from './estado-vazio';
import { MensagemErro } from './mensagem-erro';

@Component({
  imports: [Esqueleto, EstadoVazio, MensagemErro],
  template: `
    <app-esqueleto [formato]="formato()" [quantidade]="3" [rotulo]="rotulo()" />
    <app-estado-vazio titulo="Nada aqui" texto="Tente outra busca.">
      <a href="/medicamentos">Buscar</a>
    </app-estado-vazio>
    <app-estado-vazio titulo="Sem ação" />
    <app-mensagem-erro titulo="Falhou" (tentarDeNovo)="tentativas = tentativas + 1" />
  `,
})
class Estados {
  readonly formato = signal<FormatoEsqueleto>('cartao');
  readonly rotulo = signal('Carregando sua lista');
  tentativas = 0;
}

describe('estados padrão (esqueleto, vazio e erro)', () => {
  let online: boolean;

  beforeEach(() => {
    online = true;
    spyOnProperty(navigator, 'onLine').and.callFake(() => online);
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function montar() {
    const fixture = TestBed.createComponent(Estados);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  /** Texto dos parágrafos, botões e links, separados por " | ". */
  const texto = (e: Element | null) =>
    Array.from(e!.querySelectorAll('p, button, a'))
      .map((x) => x.textContent!.replace(/\s+/g, ' ').trim())
      .join(' | ');

  it('o esqueleto anuncia o carregamento e repete o formato pedido', () => {
    const { fixture, el } = montar();
    const esqueleto = el.querySelector('app-esqueleto')!;
    expect(esqueleto.getAttribute('role')).toBe('status');
    expect(esqueleto.getAttribute('aria-label')).toBe('Carregando sua lista');
    expect(esqueleto.querySelectorAll('.surface-card').length).toBe(3);

    fixture.componentInstance.formato.set('linha');
    fixture.componentInstance.rotulo.set('');
    fixture.detectChanges();
    expect(esqueleto.querySelectorAll('.surface-card').length).toBe(0);
    expect(esqueleto.querySelectorAll('[aria-hidden="true"]').length).toBe(3);
    // Sem rótulo, é só visual (não anuncia de novo).
    expect(esqueleto.getAttribute('role')).toBeNull();
  });

  it('o estado vazio mostra título, explicação e a ação projetada', () => {
    const [comAcao, semAcao] = Array.from(montar().el.querySelectorAll('app-estado-vazio'));
    expect(texto(comAcao)).toBe('Nada aqui | Tente outra busca. | Buscar');
    expect(comAcao.querySelector('a')!.getAttribute('href')).toBe('/medicamentos');
    expect(texto(semAcao)).toBe('Sem ação');
  });

  it('o erro é um alerta com "Tentar de novo"', () => {
    const { fixture, el } = montar();
    const alerta = el.querySelector('app-mensagem-erro [role="alert"]')!;
    expect(texto(alerta)).toBe('Falhou | Verifique a conexão e tente de novo. | Tentar de novo');
    alerta.querySelector('button')!.click();
    expect(fixture.componentInstance.tentativas).toBe(1);
  });

  it('sem internet, o erro explica que os dados não estão salvos no aparelho', () => {
    online = false;
    const { el } = montar();
    expect(texto(el.querySelector('app-mensagem-erro'))).toBe(
      'Sem internet | Estes dados ainda não foram salvos neste aparelho. Conecte-se e tente de novo. | Tentar de novo',
    );
  });
});
