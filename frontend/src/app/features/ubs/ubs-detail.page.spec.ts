import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ItemEstoque } from '../../core/models/estoque.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { minutosDesde, nivelSincronizacao, tempoDesde } from '../../core/sincronizacao';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { UbsDetailPage } from './ubs-detail.page';

const HORA = 3_600_000;
const haHoras = (h: number) => new Date(Date.now() - h * HORA).toISOString();

const ubs = (parcial: Partial<Ubs> = {}): Ubs => ({
  id: 7,
  cnes: '9000107',
  nome: 'UBS Clima Bom',
  tipo: 'UBS',
  bairro: 'Clima Bom',
  endereco: 'Rua Projetada B, 45',
  telefone: '(82) 3000-0107',
  horario: 'Seg a Sex · 7h às 17h',
  latitude: -9.6,
  longitude: -35.7,
  ultimaSincronizacao: haHoras(0.1),
  ...parcial,
});

const item = (
  id: number,
  nome: string,
  status: StatusEstoque,
  quantidade: number,
): ItemEstoque => ({
  medicamento: {
    id,
    catmat: `${id}`,
    nome,
    concentracao: '10 mg',
    formaFarmaceutica: 'comprimido',
    categoria: 'Hipertensão',
    rename: true,
  },
  status,
  quantidade,
  atualizadoEm: '',
});

const ESTOQUE = [
  item(1, 'Losartana', 'DISPONIVEL', 80),
  item(2, 'Captopril', 'RESERVADO', 6),
  item(3, 'Metformina', 'EM_FALTA', 0),
  // DISPONIVEL sem quantidade é exibido (e contado) como em falta.
  item(4, 'Anlodipino', 'DISPONIVEL', 0),
];

describe('sincronizacao', () => {
  it('fica em dia até 1 h, atrasada até 24 h e crítica depois ou se nunca sincronizou', () => {
    const agora = Date.parse('2026-11-18T12:00:00Z');
    const min = (iso: string) => minutosDesde(iso, agora);
    expect(nivelSincronizacao(min('2026-11-18T11:00:00Z'))).toBe('em-dia');
    expect(nivelSincronizacao(min('2026-11-18T10:59:00Z'))).toBe('atrasada');
    expect(nivelSincronizacao(min('2026-11-17T12:00:00Z'))).toBe('atrasada');
    expect(nivelSincronizacao(min('2026-11-17T11:59:00Z'))).toBe('critica');
    expect(nivelSincronizacao(minutosDesde(null))).toBe('critica');
  });

  it('descreve o tempo em min, h e dias, com singular para 1 dia', () => {
    expect(tempoDesde(10)).toBe('há 10 min');
    expect(tempoDesde(180)).toBe('há 3 h');
    expect(tempoDesde(1500)).toBe('há 1 dia');
    expect(tempoDesde(3 * 1440)).toBe('há 3 dias');
  });
});

describe('UbsDetailPage', () => {
  let http: HttpTestingController;

  /** Módulo de teste limpo, sem UBS de referência (também para abrir a página de novo no mesmo teste). */
  function reiniciar() {
    TestBed.resetTestingModule();
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: null, buscasRecentes: [] }),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(reiniciar);

  afterEach(() => http.verify());

  function abrir(dados: Ubs | null, estoque: ItemEstoque[] = ESTOQUE, status = 404) {
    const fixture = TestBed.createComponent(UbsDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    const reqUbs = http.expectOne('/api/ubs/7');
    // O estoque vem paginado (100 itens por página); com até 100 itens, uma página só.
    const reqEstoque = http.expectOne((r) => r.url === '/api/ubs/7/estoque');
    expect(reqEstoque.request.params.get('pagina')).toBe('0');
    expect(reqEstoque.request.params.get('tamanho')).toBe('100');
    if (dados) {
      reqUbs.flush(dados);
      reqEstoque.flush({
        itens: estoque,
        pagina: 0,
        tamanho: 100,
        totalItens: estoque.length,
        totalPaginas: estoque.length ? 1 : 0,
      });
    } else if (status === 0) {
      reqUbs.error(new ProgressEvent('error')); // sem conexão
    } else {
      reqUbs.flush(null, { status, statusText: 'Erro' });
    }
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const texto = (el: Element | null) => el!.textContent!.replace(/\s+/g, ' ').trim();
  const botao = (el: HTMLElement, rotulo: string) =>
    Array.from(el.querySelectorAll<HTMLElement>('a, button')).find(
      (b) => texto(b) === rotulo || texto(b).startsWith(`${rotulo} ·`),
    )!;
  const itens = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[aria-label="Itens do estoque"] li')).map(texto);

  it('mostra CNES, endereço, horário e telefone da unidade', () => {
    const { el } = abrir(ubs());
    const cartao = texto(el.querySelector('[aria-labelledby="titulo-ubs"]'));
    expect(cartao).toContain('UBS Clima Bom');
    expect(cartao).toContain('CNES 9000107');
    expect(cartao).toContain('Rua Projetada B, 45 · Clima Bom');
    expect(cartao).toContain('Seg a Sex · 7h às 17h');
    expect(cartao).toContain('(82) 3000-0107');
    expect(cartao).toContain('Sincronizado há');
    expect(el.querySelector('[data-aviso="sincronizacao"]')).toBeNull();
  });

  it('Rota abre o Google Maps e Ligar disca o telefone', () => {
    const { el } = abrir(ubs());
    expect(botao(el, 'Rota').getAttribute('href')).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=-9.6,-35.7',
    );
    expect(botao(el, 'Ligar').getAttribute('href')).toBe('tel:8230000107');
  });

  it('sem telefone, desativa Ligar e pede para confirmar na unidade', () => {
    const { el } = abrir(ubs({ telefone: null, ultimaSincronizacao: haHoras(30) }));
    expect((botao(el, 'Ligar') as HTMLButtonElement).disabled).toBeTrue();
    expect(texto(el)).toContain('Telefone não informado');
    expect(texto(el.querySelector('[data-aviso="sincronizacao"]'))).toContain(
      'Confirme na unidade antes de ir.',
    );
  });

  it('avisa quando a sincronização está atrasada ou crítica', () => {
    const atrasada = abrir(ubs({ ultimaSincronizacao: haHoras(5) })).el;
    expect(texto(atrasada.querySelector('[data-aviso="sincronizacao"]'))).toBe(
      'Última sincronização há 5 h. O estoque pode ter mudado desde então.',
    );

    reiniciar();
    const critica = abrir(ubs({ ultimaSincronizacao: haHoras(72) })).el;
    expect(texto(critica.querySelector('[data-aviso="sincronizacao"]'))).toBe(
      'Última sincronização há 3 dias. O estoque abaixo pode estar desatualizado. Ligue antes de ir.',
    );
  });

  it('avisa quando a unidade nunca sincronizou', () => {
    const { el } = abrir(ubs({ ultimaSincronizacao: null }));
    expect(texto(el.querySelector('[data-aviso="sincronizacao"]'))).toContain(
      'Esta unidade ainda não sincronizou o estoque.',
    );
  });

  it('Fixar define e desfaz a UBS de referência', () => {
    const { fixture, el } = abrir(ubs());
    const prefs = TestBed.inject(PreferenciasService);

    botao(el, 'Fixar').click();
    fixture.detectChanges();
    expect(prefs.ubsReferenciaId()).toBe(7);
    expect(botao(el, 'Minha').getAttribute('aria-pressed')).toBe('true');
    expect(texto(el)).toContain('Sua UBS de referência');

    botao(el, 'Minha').click();
    fixture.detectChanges();
    expect(prefs.ubsReferenciaId()).toBeNull();
    expect(botao(el, 'Fixar').getAttribute('aria-pressed')).toBe('false');
  });

  it('conta por situação e filtra o estoque pelo chip escolhido', () => {
    const { fixture, el } = abrir(ubs());
    expect(itens(el)).toEqual([
      'Anlodipino 10 mg Hipertensão · Sem estoque Em falta',
      'Captopril 10 mg Hipertensão · 6 un. reservadas Reservado',
      'Losartana 10 mg Hipertensão · ~80 un. em estoque Disponível',
      'Metformina 10 mg Hipertensão · Sem estoque Em falta',
    ]);
    const chips = Array.from(el.querySelectorAll('app-chip button')).map(texto);
    expect(chips).toEqual(['Todos · 4', 'Disponível · 1', 'Reservado · 1', 'Em falta · 2']);

    botao(el, 'Em falta').click();
    fixture.detectChanges();
    expect(itens(el).map((t) => t.split(' ')[0])).toEqual(['Anlodipino', 'Metformina']);
    expect(botao(el, 'Em falta').getAttribute('aria-pressed')).toBe('true');
    expect(botao(el, 'Todos').getAttribute('aria-pressed')).toBe('false');

    // Clicar de novo no filtro ativo volta para "Todos".
    botao(el, 'Em falta').click();
    fixture.detectChanges();
    expect(itens(el).length).toBe(4);
    expect(botao(el, 'Todos').getAttribute('aria-pressed')).toBe('true');
  });

  it('cada item leva ao detalhe do medicamento', () => {
    const { el } = abrir(ubs());
    const links = Array.from(
      el.querySelectorAll<HTMLAnchorElement>('[aria-label="Itens do estoque"] a'),
    ).map((a) => a.getAttribute('href'));
    expect(links).toEqual([
      '/medicamentos/4',
      '/medicamentos/2',
      '/medicamentos/1',
      '/medicamentos/3',
    ]);
  });

  it('mostra mensagem própria para filtro sem itens e para estoque vazio', () => {
    const comItens = abrir(ubs(), [item(1, 'Losartana', 'DISPONIVEL', 80)]);
    botao(comItens.el, 'Reservado').click();
    comItens.fixture.detectChanges();
    expect(itens(comItens.el)).toEqual(['Nenhum medicamento com essa situação Ver todos']);
    botao(comItens.el, 'Ver todos').click();
    comItens.fixture.detectChanges();
    expect(itens(comItens.el).length).toBe(1);

    reiniciar();
    expect(itens(abrir(ubs(), []).el)).toEqual([
      'Sem itens de estoque registrados Esta unidade ainda não informou o estoque da farmácia.',
    ]);
  });

  it('mostra o esqueleto enquanto carrega', () => {
    const fixture = TestBed.createComponent(UbsDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-esqueleto')?.getAttribute('aria-label')).toBe(
      'Carregando a unidade',
    );
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/ubs/7').flush(ubs());
    http
      .expectOne((r) => r.url === '/api/ubs/7/estoque')
      .flush({
        itens: [],
        pagina: 0,
        tamanho: 100,
        totalItens: 0,
        totalPaginas: 0,
      });
    fixture.detectChanges();
    expect(el.querySelector('app-esqueleto')).toBeNull();
  });

  it('diferencia unidade inexistente de falha de conexão', () => {
    const naoEncontrada = abrir(null, [], 404).el;
    expect(texto(naoEncontrada)).toContain('Unidade não encontrada');
    expect(botao(naoEncontrada, 'Abrir mapa').getAttribute('href')).toBe('/mapa');

    reiniciar();
    const erro = abrir(null, [], 0);
    expect(texto(erro.el.querySelector('[role="alert"]'))).toContain(
      'Não foi possível carregar a unidade',
    );

    // "Tentar de novo" busca de novo e mostra a unidade.
    botao(erro.el, 'Tentar de novo').click();
    erro.fixture.detectChanges();
    http.expectOne('/api/ubs/7').flush(ubs());
    http
      .expectOne((r) => r.url === '/api/ubs/7/estoque')
      .flush({
        itens: ESTOQUE,
        pagina: 0,
        tamanho: 100,
        totalItens: ESTOQUE.length,
        totalPaginas: 1,
      });
    erro.fixture.detectChanges();
    expect(texto(erro.el.querySelector('h1'))).toBe('UBS Clima Bom');
  });
});
