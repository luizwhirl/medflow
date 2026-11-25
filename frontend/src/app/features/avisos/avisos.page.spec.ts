import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Aviso } from '../../core/models/aviso.model';
import { AvisosPage } from './avisos.page';

const AVISOS: Aviso[] = [
  {
    id: 3,
    tipo: 'MEDICAMENTO_DISPONIVEL',
    titulo: 'Medicamento disponível',
    texto: 'O medicamento Metformina está disponível na unidade UBS Clima Bom.',
    lido: false,
    criadoEm: '2026-11-24T10:00:00Z',
  },
  {
    id: 2,
    tipo: 'MEDICAMENTO_DISPONIVEL',
    titulo: 'Medicamento disponível',
    texto: 'O medicamento Losartana está disponível na unidade UBS Clima Bom.',
    lido: true,
    criadoEm: '2026-11-23T10:00:00Z',
  },
  {
    id: 1,
    tipo: 'UBS_DADOS_ATRASADOS',
    titulo: 'Dados da UBS desatualizados',
    texto: 'Os dados de estoque da unidade UBS Clima Bom estão desatualizados.',
    lido: false,
    criadoEm: '2026-11-22T10:00:00Z',
  },
];

describe('AvisosPage', () => {
  let http: HttpTestingController;

  function abrir() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AvisosPage);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function responder(avisos: Aviso[], naoLidos: number): void {
    http.expectOne({ method: 'GET', url: '/api/avisos' }).flush(avisos);
    http.expectOne('/api/avisos/nao-lidos/total').flush({ naoLidos });
  }

  afterEach(() => http.verify());

  const texto = (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim();
  const itens = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLElement>('[aria-label="Lista de avisos"] [data-lido]'));
  const botao = (el: HTMLElement, rotulo: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => texto(b).startsWith(rotulo))!;

  function carregar() {
    const pagina = abrir();
    responder(AVISOS, 2);
    pagina.fixture.detectChanges();
    return pagina;
  }

  it('lista os avisos com o texto da API e a marcação de não lido', () => {
    const { el } = carregar();
    const [disponivel, lido, atraso] = itens(el);

    expect(texto(disponivel)).toContain('Medicamento disponível (não lido)');
    expect(texto(disponivel)).toContain('Metformina está disponível na unidade UBS Clima Bom');
    expect(disponivel.getAttribute('data-lido')).toBe('false');

    expect(texto(lido)).not.toContain('(não lido)');
    expect(lido.getAttribute('data-lido')).toBe('true');

    expect(texto(atraso)).toContain('Dados da UBS desatualizados');
  });

  it('filtra os não lidos e marca todos como lidos na API', () => {
    const { fixture, el } = carregar();
    expect(texto(botao(el, 'Todos'))).toBe('Todos · 3');
    expect(texto(botao(el, 'Não lidos'))).toBe('Não lidos · 2');

    botao(el, 'Não lidos').click();
    fixture.detectChanges();
    expect(itens(el).length).toBe(2);

    botao(el, 'Marcar todos como lidos').click();
    http.expectOne({ method: 'PATCH', url: '/api/avisos/lidas' }).flush({ atualizados: 2 });
    fixture.detectChanges();
    expect(itens(el).length).toBe(0);
    expect(texto(el.querySelector('[aria-label="Lista de avisos"]')!)).toBe(
      'Você leu todos os avisos.',
    );
    expect(botao(el, 'Marcar todos como lidos')).toBeUndefined();
  });

  it('sem avisos, explica como passar a recebê-los', () => {
    const { fixture, el } = abrir();
    responder([], 0);
    fixture.detectChanges();
    expect(texto(el)).toContain('Ative o aviso nos remédios da Minha lista');
    expect(el.querySelector('a[href="/lista"]')).not.toBeNull();
  });

  it('falha ao carregar permite tentar de novo', () => {
    const { fixture, el } = abrir();
    http.expectOne({ method: 'GET', url: '/api/avisos' }).error(new ProgressEvent('error'));
    http.expectOne('/api/avisos/nao-lidos/total');
    fixture.detectChanges();
    expect(texto(el)).toContain('Não foi possível carregar os avisos agora');

    botao(el, 'Tentar de novo').click();
    responder([], 0);
    fixture.detectChanges();
    expect(texto(el)).toContain('Nenhum aviso agora');
  });
});
