import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { ConexaoService } from '../../core/offline/conexao.service';
import { BannerOffline, quando } from './banner-offline';

@Component({ template: '' })
class Vazia {}

describe('BannerOffline', () => {
  let online: boolean;

  beforeEach(() => {
    online = true;
    spyOnProperty(navigator, 'onLine').and.callFake(() => online);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'mapa', component: Vazia },
          { path: 'lista', component: Vazia },
        ]),
      ],
    });
  });

  function montar() {
    const fixture = TestBed.createComponent(BannerOffline);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      conexao: TestBed.inject(ConexaoService),
      banner: () => el.querySelector('[data-banner="conexao"]'),
      texto: () => el.textContent!.replace(/\s+/g, ' ').trim(),
    };
  }

  const mudarRede = (valor: boolean) => {
    online = valor;
    window.dispatchEvent(new Event(valor ? 'online' : 'offline'));
  };

  it('fica escondido com internet e sem dados salvos em uso', () => {
    expect(montar().banner()).toBeNull();
  });

  it('sem internet, avisa mesmo sem dados salvos em uso', () => {
    const t = montar();
    mudarRede(false);
    t.fixture.detectChanges();
    expect(t.texto()).toBe(
      'Sem internet. Você vê o que já foi carregado; o resto aparece quando a conexão voltar.',
    );
    expect(t.texto()).not.toContain('Atualizar');
  });

  it('sem internet e com dados salvos, mostra o horário deles', () => {
    const t = montar();
    mudarRede(false);
    const hoje = new Date();
    hoje.setHours(8, 5, 0, 0);
    t.conexao.registrarDadosSalvos(hoje.getTime());
    t.fixture.detectChanges();
    expect(t.texto()).toBe('Sem internet. Mostrando dados salvos hoje às 08:05.');
  });

  it('com internet mas sem resposta da API, oferece atualizar', () => {
    const t = montar();
    const recarregar = spyOn(t.conexao, 'tentarDeNovo');
    t.conexao.registrarDadosSalvos(new Date(2026, 10, 20, 18, 0).getTime());
    t.fixture.detectChanges();
    expect(t.texto()).toContain('Não foi possível atualizar. Mostrando dados salvos');
    (t.fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    expect(recarregar).toHaveBeenCalled();
  });

  it('volta a esconder ao mudar de tela, mas não ao mudar só os parâmetros', async () => {
    const t = montar();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/mapa');
    t.conexao.registrarDadosSalvos(1_000);
    await router.navigateByUrl('/mapa?raio=3');
    expect(t.conexao.dadosSalvosEm()).toBe(1_000);
    await router.navigateByUrl('/lista');
    expect(t.conexao.dadosSalvosEm()).toBeNull();
  });

  it('quando: hoje, ontem ou a data', () => {
    const agora = new Date(2026, 10, 24, 10, 0).getTime();
    expect(quando(new Date(2026, 10, 24, 7, 3).getTime(), agora)).toBe('hoje às 07:03');
    expect(quando(new Date(2026, 10, 23, 22, 15).getTime(), agora)).toBe('ontem às 22:15');
    expect(quando(new Date(2026, 10, 2, 9, 0).getTime(), agora)).toBe('em 02/11 às 09:00');
  });
});
