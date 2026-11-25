import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Medicamento } from '../../core/models/medicamento.model';
import { EtapaRemessa, RemessaRastreamento } from '../../core/models/rastreamento.model';
import { Ubs, UbsProxima } from '../../core/models/ubs.model';
import { Timeline } from '../../shared/components/timeline';
import { MedicamentoSearchPage } from './medicamento-search.page';

/** Resposta paginada da API (primeira página com todos os itens). */
const pagina = <T>(itens: T[]) => ({
  itens,
  pagina: 0,
  tamanho: 20,
  totalItens: itens.length,
  totalPaginas: itens.length ? 1 : 0,
});

const metformina: Medicamento = {
  id: 1,
  catmat: 'BR0267691',
  nome: 'Metformina',
  concentracao: '850 mg',
  formaFarmaceutica: 'comprimido',
  categoria: 'Diabetes',
  rename: true,
};

/**
 * UBS todas no mesmo ponto da origem: distância e raio só podem vir da API de proximidade.
 * `km` é a distância que a API devolve para ela.
 */
const ubs = (id: number, nome: string, km: number) =>
  ({ id, nome, bairro: 'Centro', latitude: 0, longitude: 0, distanciaKm: km }) as Ubs & UbsProxima;

const ETAPAS: EtapaRemessa[] = [
  'PEDIDO',
  'SEPARADO_CAF',
  'EM_TRANSPORTE',
  'RECEBIDO',
  'DISPONIVEL',
];

/** Remessa da API com as datas informadas; previsão qui, 26/11. */
function remessa(datas: Partial<Record<EtapaRemessa, string>>): RemessaRastreamento {
  return {
    id: 4,
    quantidade: 300,
    previsaoChegada: '2026-11-26',
    etapaAtual: ETAPAS.filter((e) => datas[e]).at(-1) ?? 'PEDIDO',
    concluida: !!datas.DISPONIVEL,
    linhaDoTempo: ETAPAS.map((etapa) => ({ etapa, ocorridoEm: datas[etapa] ?? null })),
  };
}

const EM_TRANSITO = {
  PEDIDO: '2026-11-17T12:00:00Z',
  SEPARADO_CAF: '2026-11-17T20:00:00Z',
  EM_TRANSPORTE: '2026-11-18T13:00:00Z',
};

describe('Timeline', () => {
  it('marca como concluídas as etapas até a atual e destaca a seguinte', () => {
    const fixture = TestBed.createComponent(Timeline);
    fixture.componentRef.setInput('etapas', [{ titulo: 'A' }, { titulo: 'B' }, { titulo: 'C' }]);
    fixture.componentRef.setInput('atual', 0);
    fixture.detectChanges();
    const itens = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('li'));
    expect(itens.map((li) => li.textContent!.includes('(concluída)'))).toEqual([
      true,
      false,
      false,
    ]);
    expect(itens[0].getAttribute('aria-current')).toBe('step');
  });
});

describe('MedicamentoSearchPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('medflow.buscas-contadas');
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 1, buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok) =>
      ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.removeItem('medflow.preferencias');
    localStorage.removeItem('medflow.buscas-contadas');
  });

  function resultados(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('[aria-label="Resultados"] a')).map((a) =>
      a.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('conta só as UBS que a API devolve a até 5 km e respeita "Só disponíveis"', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([{ id: 10, ubsId: 1, medicamentoId: 2 }]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([ubs(1, 'Perto', 1), ubs(2, 'Meio', 3)]);
    tick(250);
    http
      .expectOne('/api/medicamentos')
      .flush(pagina([metformina, { ...metformina, id: 2, nome: 'Glibenclamida' }]));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(1, 'Perto', 1), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
      { ubs: ubs(2, 'Meio', 3), status: 'EM_FALTA', quantidade: 0, atualizadoEm: '' },
      { ubs: ubs(3, 'Longe', 9), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
    ]);
    http
      .expectOne('/api/medicamentos/2/disponibilidade')
      .flush([{ ubs: ubs(3, 'Longe', 9), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' }]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(resultados(el)).toEqual([
      'Metformina 850 mg comprimido Em 1 de 2 UBS próximas',
      jasmine.stringContaining('Nenhuma UBS a até 5 km'),
    ]);
    // Glibenclamida está na lista de desejos do aparelho.
    expect(resultados(el)[1]).toContain('Glibenclamida 850 mg Na minha lista');

    const soDisponiveis = Array.from(el.querySelectorAll('app-chip button')).find((b) =>
      b.textContent!.includes('Só disponíveis'),
    ) as HTMLButtonElement;
    soDisponiveis.click();
    fixture.detectChanges();
    expect(resultados(el).length).toBe(1);
    http.verify();
  }));

  it('desligar "Até 5 km" conta todas as UBS sem nova chamada', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([ubs(1, 'Perto', 1)]);
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([metformina]));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(1, 'Perto', 1), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
      { ubs: ubs(3, 'Longe', 9), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(resultados(el)).toEqual(['Metformina 850 mg comprimido Em 1 de 1 UBS próximas']);

    const ateCinco = Array.from(el.querySelectorAll('app-chip button')).find((b) =>
      b.textContent!.includes('5 km'),
    ) as HTMLButtonElement;
    ateCinco.click();
    fixture.detectChanges();
    expect(resultados(el)[0]).toContain('2 de 2');
    http.verify();
  }));

  it('"Abertas agora" conta só as UBS que a API diz estarem abertas', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([ubs(1, 'Perto', 1), ubs(2, 'Meio', 3)]);
    tick(250);
    http
      .expectOne('/api/medicamentos')
      .flush(pagina([metformina, { ...metformina, id: 2, nome: 'Glibenclamida' }]));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(1, 'Perto', 1), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
      { ubs: ubs(2, 'Meio', 3), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
    ]);
    http
      .expectOne('/api/medicamentos/2/disponibilidade')
      .flush([{ ubs: ubs(1, 'Perto', 1), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' }]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(resultados(el)).toEqual([
      'Metformina 850 mg comprimido Em 2 de 2 UBS próximas',
      'Glibenclamida 850 mg comprimido Em 1 de 1 UBS próximas',
    ]);

    const abertas = Array.from(el.querySelectorAll('app-chip button')).find((b) =>
      b.textContent!.includes('Abertas agora'),
    ) as HTMLButtonElement;
    abertas.click();
    fixture.detectChanges();
    // Só a "Meio" está aberta: a "Perto" sai das contas
    http.expectOne('/api/ubs?abertas=true').flush([ubs(2, 'Meio', 3)]);
    fixture.detectChanges();
    expect(resultados(el)).toEqual([
      'Metformina 850 mg comprimido Em 1 de 1 UBS próximas abertas',
      'Glibenclamida 850 mg comprimido Nenhuma UBS a até 5 km aberta agora',
    ]);

    // Desligar o chip volta às contas anteriores sem nova chamada
    abertas.click();
    fixture.detectChanges();
    expect(resultados(el)[0]).toContain('2 de 2');
    http.verify();
  }));

  it('filtra pela categoria escolhida no chip', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/medicamentos/categorias').flush(['Diabetes']);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([]);
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([]));
    fixture.detectChanges();

    const chip = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-chip button'),
    ).find((b) => b.textContent!.includes('Diabetes')) as HTMLButtonElement;
    chip.click();
    fixture.detectChanges();
    tick(250);

    const req = http.expectOne((r) => r.url === '/api/medicamentos');
    expect(req.request.params.get('categoria')).toBe('Diabetes');
    req.flush([]);
    http.verify();
  }));

  /** Abre o detalhe do medicamento 1, responde a disponibilidade e a remessa e devolve a página. */
  function abrirDetalhe(
    disponibilidade: unknown[],
    resposta: RemessaRastreamento | null,
    { comReferencia = true } = {},
  ): HTMLElement {
    if (!comReferencia) {
      localStorage.setItem(
        'medflow.preferencias',
        JSON.stringify({ ubsReferenciaId: null, remediosContinuos: [], buscasRecentes: [] }),
      );
    }
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http.expectOne('/api/medicamentos/1/disponibilidade').flush(disponibilidade);
    http.expectOne({ method: 'POST', url: '/api/medicamentos/1/buscas' }).flush(null);
    if (comReferencia) {
      http
        .expectOne('/api/medicamentos/1/rastreamento?ubsId=1')
        .flush({ medicamentoId: 1, ubsId: 1, remessa: resposta });
    }
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&medicamentoId=1').flush([]);
    // horário de funcionamento das UBS (PDS-43)
    http.expectOne('/api/ubs').flush([]);
    http.expectOne('/api/lista-desejos').flush([]);
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([]));
    fixture.detectChanges();
    http.verify();
    return fixture.nativeElement as HTMLElement;
  }

  function passos(el: HTMLElement) {
    return Array.from(el.querySelectorAll('app-timeline li')).map((li) => ({
      concluido: li.textContent!.includes('(concluída)'),
      texto: li.textContent!.replace(/\s+/g, ' ').trim(),
    }));
  }

  it('mostra as etapas e datas da remessa a caminho e a previsão no alerta de falta', fakeAsync(() => {
    const el = abrirDetalhe(
      [
        {
          ubs: ubs(1, 'UBS Benedito Bentes I', 0.26),
          status: 'EM_FALTA',
          quantidade: 0,
          atualizadoEm: '',
        },
      ],
      remessa(EM_TRANSITO),
    );

    const p = passos(el);
    expect(p.map((x) => x.concluido)).toEqual([true, true, true, false]);
    expect(p[0].texto).toContain('ter, 17/11');
    expect(p[2].texto).toContain('qua, 18/11');
    expect(p[3].texto).toContain('Previsão: qui, 26/11');
    const alerta = el.querySelector('[role="status"]')!.textContent!.replace(/\s+/g, ' ');
    expect(alerta).toContain('Próxima remessa chega qui, 26/11.');
  }));

  it('remessa entregue conclui todas as etapas e não aparece no alerta', fakeAsync(() => {
    const el = abrirDetalhe(
      [
        {
          ubs: ubs(1, 'UBS Benedito Bentes I', 0.26),
          status: 'EM_FALTA',
          quantidade: 0,
          atualizadoEm: '',
        },
      ],
      remessa({
        ...EM_TRANSITO,
        RECEBIDO: '2026-11-19T15:00:00Z',
        DISPONIVEL: '2026-11-19T18:00:00Z',
      }),
    );

    expect(passos(el).every((x) => x.concluido)).toBeTrue();
    expect(el.querySelector('[role="status"]')!.textContent).not.toContain('Próxima remessa');
  }));

  it('sem remessa para a UBS, nenhuma etapa concluída e aviso de que não há remessa', fakeAsync(() => {
    const el = abrirDetalhe([], null);

    expect(passos(el).some((x) => x.concluido)).toBeFalse();
    expect(el.querySelector('[aria-labelledby="titulo-remessa"]')!.textContent).toContain(
      'Nenhuma remessa deste remédio para a sua UBS.',
    );
  }));

  it('sem UBS de referência, não consulta a remessa e pede para escolher uma', fakeAsync(() => {
    const el = abrirDetalhe([], null, { comReferencia: false });

    const card = el.querySelector('[aria-labelledby="titulo-remessa"]')!;
    expect(card.textContent).toContain('Escolha sua UBS');
    expect(card.querySelector('a[href="/mapa"]')).not.toBeNull();
  }));

  /** Responde as chamadas a `url` que ainda estão abertas (as canceladas com o erro ficam de fora). */
  function pendentes(url: string) {
    http
      .match(url)
      .filter((r) => !r.cancelled)
      .forEach((r) => r.flush([]));
  }

  /** Abre a busca e responde categorias e raio; a busca em si fica pendente. */
  function abrirBusca() {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([]);
    http.expectOne('/api/lista-desejos').flush([]);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('mostra o esqueleto durante a busca e o erro com "Tentar de novo"', fakeAsync(() => {
    const { fixture, el } = abrirBusca();
    expect(el.querySelector('app-esqueleto')?.getAttribute('aria-label')).toBe(
      'Buscando medicamentos',
    );
    expect(el.textContent).not.toContain('Nenhum medicamento encontrado');

    tick(250);
    http.expectOne('/api/medicamentos').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain(
      'Não foi possível buscar os medicamentos',
    );
    expect(el.textContent).not.toContain('0 resultados');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([metformina]));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([]);
    fixture.detectChanges();
    expect(resultados(el)).toEqual(['Metformina 850 mg comprimido Nenhuma UBS a até 5 km']);
    http.verify();
  }));

  it('sem resultados, mostra o estado vazio com uma dica', fakeAsync(() => {
    const { fixture, el } = abrirBusca();
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([]));
    fixture.detectChanges();
    const vazio = el.querySelector('[aria-label="Resultados"] app-estado-vazio')!;
    expect(vazio.textContent).toContain('Nenhum medicamento encontrado');
    expect(vazio.textContent).toContain('Confira o nome ou tente outro termo');
    http.verify();
  }));

  it('no detalhe, diferencia medicamento inexistente de falha de conexão', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '99');
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&raioKm=5').flush([]);
    http.expectOne('/api/ubs?lat=0&lng=0&medicamentoId=99').flush([]);
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/ubs').flush([]);
    http.expectOne('/api/medicamentos/99').flush(null, { status: 404, statusText: 'Não achado' });
    pendentes('/api/medicamentos/99/disponibilidade');
    http
      .expectOne('/api/medicamentos/99/rastreamento?ubsId=1')
      .flush({ medicamentoId: 99, ubsId: 1, remessa: null });
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([]));
    fixture.detectChanges();

    const detalhe = (fixture.nativeElement as HTMLElement).querySelector(
      'app-rastreio-medicamento',
    )!;
    expect(detalhe.textContent).toContain('Medicamento não encontrado');
    expect(detalhe.querySelector('a[href="/medicamentos"]')).not.toBeNull();

    fixture.componentRef.setInput('id', '98');
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/98').error(new ProgressEvent('error'));
    pendentes('/api/medicamentos/98/disponibilidade');
    http
      .expectOne('/api/medicamentos/98/rastreamento?ubsId=1')
      .flush({ medicamentoId: 98, ubsId: 1, remessa: null });
    http.expectOne('/api/ubs?lat=0&lng=0&medicamentoId=98').flush([]);
    fixture.detectChanges();
    expect(detalhe.querySelector('[role="alert"]')!.textContent).toContain(
      'Não foi possível carregar o medicamento',
    );
    http.verify();
  }));

  it('no detalhe, avisa a falta na UBS de referência e aponta onde tem', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      {
        ubs: ubs(1, 'UBS Benedito Bentes I', 0.26),
        status: 'EM_FALTA',
        quantidade: 0,
        atualizadoEm: '',
      },
      {
        ubs: ubs(3, 'UBS Clima Bom', 2.4),
        status: 'DISPONIVEL',
        quantidade: 120,
        atualizadoEm: '',
      },
    ]);
    http.expectOne({ method: 'POST', url: '/api/medicamentos/1/buscas' }).flush(null);
    http
      .expectOne('/api/medicamentos/1/rastreamento?ubsId=1')
      .flush({ medicamentoId: 1, ubsId: 1, remessa: null });
    http
      .expectOne('/api/ubs?lat=0&lng=0&raioKm=5')
      .flush([ubs(1, 'UBS Benedito Bentes I', 0.26), ubs(3, 'UBS Clima Bom', 2.4)]);
    http
      .expectOne('/api/ubs?lat=0&lng=0&medicamentoId=1')
      .flush([ubs(1, 'UBS Benedito Bentes I', 0.26), ubs(3, 'UBS Clima Bom', 2.4)]);
    // O horário vem da lista de UBS: a disponibilidade do medicamento não o traz
    http.expectOne('/api/ubs').flush([
      { ...ubs(1, 'UBS Benedito Bentes I', 0.26), abertaAgora: false, fechaAs: null },
      {
        ...ubs(3, 'UBS Clima Bom', 2.4),
        abertaAgora: true,
        fechaAs: '17:00',
        fechaEm: '2999-01-01T17:00:00-03:00',
      },
    ]);
    http.expectOne('/api/lista-desejos').flush([]);
    tick(250);
    http.expectOne('/api/medicamentos').flush(pagina([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const linhaOnde = (nome: string) =>
      Array.from(el.querySelectorAll('[aria-labelledby="titulo-onde"] li'))
        .find((li) => li.textContent!.includes(nome))!
        .textContent!.replace(/\s+/g, ' ');
    expect(linhaOnde('UBS Clima Bom')).toContain('Aberta até 17h');
    expect(linhaOnde('UBS Benedito Bentes I')).toContain('Fechada agora');
    const alerta = el.querySelector('[role="status"]')!.textContent!.replace(/\s+/g, ' ');
    expect(alerta).toContain('Em falta na UBS Benedito Bentes I');
    expect(alerta).toContain('a UBS Clima Bom (2,4 km) tem estoque');
    const onde = el.querySelector('[aria-labelledby="titulo-onde"]')!.textContent!;
    expect(onde.indexOf('UBS Clima Bom')).toBeLessThan(onde.indexOf('UBS Benedito Bentes I'));
    expect(onde).toContain('~120 un.');
    expect(el.querySelectorAll('app-timeline li').length).toBe(4);
    http.verify();
  }));
});
