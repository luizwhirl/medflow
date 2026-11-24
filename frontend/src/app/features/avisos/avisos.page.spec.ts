import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Aviso } from '../../core/models/aviso.model';
import { AvisosPage } from './avisos.page';

const AVISOS: Aviso[] = [
  {
    id: 'remessa-9-EM_TRANSPORTE',
    tipo: 'REMESSA',
    ubsId: 2,
    ubsNome: 'UBS Clima Bom',
    medicamentoId: 1,
    medicamentoNome: 'Metformina',
    medicamentoConcentracao: '850 mg',
    quantidade: 300,
    etapa: 'EM_TRANSPORTE',
    previsaoChegada: '2026-11-26',
  },
  {
    id: 'disponivel-2-3',
    tipo: 'DISPONIVEL',
    ubsId: 2,
    ubsNome: 'UBS Clima Bom',
    medicamentoId: 3,
    medicamentoNome: 'Losartana',
    medicamentoConcentracao: '50 mg',
    quantidade: 80,
  },
  {
    id: 'sincronizacao-2-nunca',
    tipo: 'SINCRONIZACAO_ATRASADA',
    ubsId: 2,
    ubsNome: 'UBS Clima Bom',
  },
];

describe('AvisosPage', () => {
  let http: HttpTestingController;

  function abrir(
    ubsReferenciaId: number | null = 2,
    remediosContinuos = [1, 3],
    lidos: string[] = [],
  ) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId, remediosContinuos, buscasRecentes: [] }),
    );
    localStorage.setItem('medflow.avisos-lidos', JSON.stringify(lidos));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AvisosPage);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => http.verify());

  const texto = (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim();
  const itens = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLAnchorElement>('[aria-label="Lista de avisos"] a'));
  const botao = (el: HTMLElement, rotulo: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => texto(b).startsWith(rotulo))!;

  function carregar(lidos: string[] = []) {
    const pagina = abrir(2, [1, 3], lidos);
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1,3').flush(AVISOS);
    pagina.fixture.detectChanges();
    return pagina;
  }

  it('lista os avisos com texto, link e marcação de não lido', () => {
    const { el } = carregar(['disponivel-2-3']);
    const [remessa, disponivel, sinc] = itens(el);

    expect(texto(remessa)).toContain('Metformina 850 mg a caminho (não lido)');
    expect(texto(remessa)).toContain('Previsão: qui, 26/11.');
    expect(remessa.getAttribute('href')).toBe('/medicamentos/1');
    expect(remessa.getAttribute('data-lido')).toBe('false');

    expect(texto(disponivel)).not.toContain('(não lido)');
    expect(disponivel.getAttribute('data-lido')).toBe('true');

    expect(texto(sinc)).toContain('Dados atrasados na sua UBS');
    expect(sinc.getAttribute('href')).toBe('/ubs/2');
  });

  it('abrir um aviso o marca como lido', () => {
    const { fixture, el } = carregar();
    itens(el)[0].click();
    fixture.detectChanges();
    expect(itens(el)[0].getAttribute('data-lido')).toBe('true');
    expect(JSON.parse(localStorage.getItem('medflow.avisos-lidos')!)).toEqual([
      'remessa-9-EM_TRANSPORTE',
    ]);
  });

  it('filtra os não lidos e marca todos como lidos', () => {
    const { fixture, el } = carregar(['disponivel-2-3']);
    expect(texto(botao(el, 'Todos'))).toBe('Todos · 3');
    expect(texto(botao(el, 'Não lidos'))).toBe('Não lidos · 2');

    botao(el, 'Não lidos').click();
    fixture.detectChanges();
    expect(itens(el).length).toBe(2);

    botao(el, 'Marcar todos como lidos').click();
    fixture.detectChanges();
    expect(itens(el).length).toBe(0);
    expect(texto(el.querySelector('[aria-label="Lista de avisos"]')!)).toBe(
      'Você leu todos os avisos.',
    );
    expect(botao(el, 'Marcar todos como lidos')).toBeUndefined();
  });

  it('sem UBS de referência, pede para escolher uma sem chamar a API', () => {
    const { el } = abrir(null);
    expect(texto(el)).toContain('Escolha sua UBS de referência');
    expect(el.querySelector('a[href="/mapa"]')).not.toBeNull();
  });

  it('sem remédios de uso contínuo, explica como passar a receber avisos', () => {
    const { fixture, el } = abrir(2, []);
    http.expectOne('/api/avisos?ubsId=2').flush([]);
    fixture.detectChanges();
    expect(texto(el)).toContain('Adicione seus remédios de uso contínuo');
  });

  it('falha ao carregar permite tentar de novo', () => {
    const { fixture, el } = abrir();
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1,3').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(texto(el)).toContain('Não foi possível carregar os avisos agora');

    botao(el, 'Tentar de novo').click();
    fixture.detectChanges();
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1,3').flush([]);
    fixture.detectChanges();
    expect(texto(el)).toContain('Nenhum aviso agora');
  });
});
