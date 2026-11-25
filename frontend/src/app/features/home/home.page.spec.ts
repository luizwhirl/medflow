import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { compararDistancia, formatarDistancia } from '../../core/geo';
import { ItemListaDesejo } from '../../core/models/lista-desejos.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { Ubs } from '../../core/models/ubs.model';
import { AvisosService } from '../../core/services/avisos.service';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { HomePage } from './home.page';

const ubs = (id: number, nome: string): Ubs => ({
  id,
  cnes: `900000${id}`,
  nome,
  tipo: 'UBS',
  bairro: 'Centro',
  endereco: 'Rua A',
  telefone: null,
  horario: null,
  latitude: -9.6,
  longitude: -35.7,
  ultimaSincronizacao: null,
});

const metformina: Medicamento = {
  id: 1,
  catmat: 'BR0267691',
  nome: 'Metformina',
  concentracao: '850 mg',
  formaFarmaceutica: 'Comprimido',
  categoria: 'Diabetes',
  rename: true,
};

describe('compararDistancia', () => {
  it('ordena da mais perto para a mais longe e deixa sem distância no fim', () => {
    expect([3.2, null, 0.4, null, 1].sort(compararDistancia)).toEqual([0.4, 1, 3.2, null, null]);
  });
});

describe('formatarDistancia', () => {
  it('usa metros abaixo de 1 km e não mostra "1000 m"', () => {
    expect(formatarDistancia(0.26)).toBe('260 m');
    expect(formatarDistancia(0.996)).toBe('1,0 km');
    expect(formatarDistancia(2.43)).toBe('2,4 km');
  });
});

const desejo = (id: number, medicamentoId: number, ubsId = 7): ItemListaDesejo => ({
  id,
  ubsId,
  ubsNome: `UBS ${ubsId}`,
  medicamentoId,
  medicamentoNome: `Remédio ${medicamentoId}`,
  medicamentoConcentracao: '10 mg',
  notificar: false,
  criadoEm: '2026-11-24T10:00:00Z',
});

describe('PreferenciasService', () => {
  let http: HttpTestingController;

  function iniciar(salvo: object | null, lista: ItemListaDesejo[]): PreferenciasService {
    if (salvo) localStorage.setItem('medflow.preferencias', JSON.stringify(salvo));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const prefs = TestBed.inject(PreferenciasService);
    http.expectOne({ method: 'GET', url: '/api/lista-desejos' }).flush(lista);
    return prefs;
  }

  beforeEach(() => localStorage.removeItem('medflow.preferencias'));
  afterEach(() => {
    http.verify();
    localStorage.removeItem('medflow.preferencias');
  });

  it('usa os remédios da lista de desejos, sem repetir o que está em mais de uma UBS', () => {
    const prefs = iniciar(null, [desejo(10, 5), desejo(11, 3), desejo(12, 5, 8)]);
    expect(prefs.remediosContinuos()).toEqual([5, 3]);
  });

  it('adiciona o remédio na UBS de referência, sem aviso, e remove de todas as UBS', () => {
    const prefs = iniciar({ ubsReferenciaId: 7 }, [desejo(10, 5), desejo(12, 5, 8)]);

    prefs.alternarRemedioContinuo(3).subscribe();
    const post = http.expectOne({ method: 'POST', url: '/api/lista-desejos' });
    expect(post.request.body).toEqual({ ubsId: 7, medicamentoId: 3, notificar: false });
    post.flush(desejo(13, 3));
    expect(prefs.remediosContinuos()).toEqual([3, 5]);

    prefs.alternarRemedioContinuo(5).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/10' }).flush(null);
    http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/12' }).flush(null);
    expect(prefs.remediosContinuos()).toEqual([3]);
  });

  it('sem UBS de referência, não tem onde adicionar', () => {
    const prefs = iniciar(null, []);
    let erro = false;
    prefs.alternarRemedioContinuo(3).subscribe({ error: () => (erro = true) });
    expect(erro).toBeTrue();
  });

  it('leva para a API os remédios que estavam só no navegador e guarda a UBS e as buscas', () => {
    const prefs = iniciar(
      { ubsReferenciaId: null, remediosContinuos: [3, 5], buscasRecentes: ['Insulina'] },
      [desejo(10, 5)],
    );
    // Sem UBS de referência não dá para migrar: espera a pessoa escolher uma.
    http.expectNone({ method: 'POST', url: '/api/lista-desejos' });

    prefs.definirUbsReferencia(7);
    const post = http.expectOne({ method: 'POST', url: '/api/lista-desejos' });
    expect(post.request.body).toEqual({ ubsId: 7, medicamentoId: 3, notificar: false });
    post.flush(desejo(11, 3));

    expect(prefs.remediosContinuos()).toEqual([3, 5]);
    expect(JSON.parse(localStorage.getItem('medflow.preferencias')!)).toEqual({
      ubsReferenciaId: 7,
      buscasRecentes: ['Insulina'],
      remediosAMigrar: [],
    });
  });

  it('o que falhar na migração fica para a próxima vez', () => {
    const prefs = iniciar({ ubsReferenciaId: 7, remediosContinuos: [3] }, []);
    http
      .expectOne({ method: 'POST', url: '/api/lista-desejos' })
      .flush(null, { status: 500, statusText: 'Erro' });

    expect(prefs.remediosContinuos()).toEqual([]);
    expect(JSON.parse(localStorage.getItem('medflow.preferencias')!).remediosAMigrar).toEqual([3]);
  });
});

describe('HomePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 2, buscasRecentes: ['Insulina'] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((_ok, erro) =>
      erro?.({} as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.removeItem('medflow.preferencias'));

  it('mostra UBS de referência, remédio contínuo com status na referência e atalhos', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    http.expectOne('/api/lista-desejos').flush([desejo(10, 1, 2)]);
    http.expectOne('/api/avisos').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush(['Diabetes', 'Hipertensão']);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http
      .expectOne('/api/medicamentos/1/rastreamento?ubsId=2')
      .flush({ medicamentoId: 1, ubsId: 2, remessa: null });
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(1, 'UBS Centro'), status: 'DISPONIVEL', quantidade: 10, atualizadoEm: '' },
      { ubs: ubs(2, 'UBS Clima Bom'), status: 'EM_FALTA', quantidade: 0, atualizadoEm: '' },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const texto = (id: string) => el.querySelector(`[aria-labelledby="${id}"]`)!.textContent!;
    expect(texto('titulo-referencia')).toContain('UBS Clima Bom');
    expect(texto('titulo-continuos')).toContain('Metformina 850 mg');
    expect(texto('titulo-continuos')).toContain('Em falta');
    expect(texto('titulo-atalhos')).toContain('Hipertensão');
    http.verify();
  });

  it('não usa o status de outra UBS na coluna "Na sua UBS" e ignora DISPONIVEL sem quantidade', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    http.expectOne('/api/lista-desejos').flush([desejo(10, 1, 2)]);
    http.expectOne('/api/avisos').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http
      .expectOne('/api/medicamentos/1/rastreamento?ubsId=2')
      .flush({ medicamentoId: 1, ubsId: 2, remessa: null });
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([
        { ubs: ubs(1, 'UBS Centro'), status: 'DISPONIVEL', quantidade: 0, atualizadoEm: '' },
      ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const continuos = el.querySelector('[aria-labelledby="titulo-continuos"]')!.textContent!;
    expect(continuos).toContain('Sem registro na sua UBS');
    expect(continuos).not.toContain('Disponível');
    expect(continuos).toContain('Nenhuma UBS com estoque');
    http.verify();
  });

  it('com localização, usa as distâncias da API na referência, em "Onde tem" e nas UBS próximas', fakeAsync(() => {
    (navigator.geolocation.getCurrentPosition as jasmine.Spy).and.callFake((ok) =>
      ok({ coords: { latitude: -9.6, longitude: -35.7 } } as GeolocationPosition),
    );
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();

    http.expectOne('/api/lista-desejos').flush([desejo(10, 1, 2)]);
    http.expectOne('/api/avisos').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http
      .expectOne('/api/medicamentos/1/rastreamento?ubsId=2')
      .flush({ medicamentoId: 1, ubsId: 2, remessa: null });
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(3, 'UBS Longe'), status: 'DISPONIVEL', quantidade: 4, atualizadoEm: '' },
      { ubs: ubs(1, 'UBS Centro'), status: 'DISPONIVEL', quantidade: 10, atualizadoEm: '' },
      { ubs: ubs(2, 'UBS Clima Bom'), status: 'EM_FALTA', quantidade: 0, atualizadoEm: '' },
    ]);
    // Todas as UBS têm a mesma coordenada: as distâncias só podem ter vindo da API.
    http.expectOne('/api/ubs?lat=-9.6&lng=-35.7').flush([
      { ...ubs(2, 'UBS Clima Bom'), distanciaKm: 0.8 },
      { ...ubs(1, 'UBS Centro'), distanciaKm: 3.21 },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const texto = (id: string) =>
      el.querySelector(`[aria-labelledby="${id}"]`)!.textContent!.replace(/\s+/g, ' ');
    expect(texto('titulo-referencia')).toContain('~800 m de você');
    expect(texto('titulo-continuos')).toContain('UBS Centro · 3,2 km');
    const cartoes = Array.from(
      el.querySelectorAll('[aria-labelledby="titulo-proximas"] a[href^="/ubs"]'),
    );
    expect(cartoes.map((a) => a.querySelector('h3')!.textContent!.trim())).toEqual([
      'UBS Clima Bom',
      'UBS Centro',
    ]);
    expect(cartoes[0].textContent).toContain('800 m');
    http.verify();
  }));

  it('mostra quando chega a remessa a caminho da UBS de referência', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    http.expectOne('/api/lista-desejos').flush([desejo(10, 1, 2)]);
    http.expectOne('/api/avisos').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http.expectOne('/api/medicamentos/1/rastreamento?ubsId=2').flush({
      medicamentoId: 1,
      ubsId: 2,
      remessa: {
        id: 4,
        quantidade: 300,
        previsaoChegada: '2026-10-22',
        etapaAtual: 'EM_TRANSPORTE',
        concluida: false,
        linhaDoTempo: [
          { etapa: 'PEDIDO', ocorridoEm: '2026-10-19T12:00:00Z' },
          { etapa: 'SEPARADO_CAF', ocorridoEm: '2026-10-20T12:00:00Z' },
          { etapa: 'EM_TRANSPORTE', ocorridoEm: '2026-10-21T12:00:00Z' },
          { etapa: 'RECEBIDO', ocorridoEm: null },
          { etapa: 'DISPONIVEL', ocorridoEm: null },
        ],
      },
    });
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([
        { ubs: ubs(2, 'UBS Clima Bom'), status: 'EM_FALTA', quantidade: 0, atualizadoEm: '' },
      ]);
    fixture.detectChanges();

    const continuos = (fixture.nativeElement as HTMLElement).querySelector(
      '[aria-labelledby="titulo-continuos"]',
    )!.textContent!;
    expect(continuos).toContain('Chega qui, 22/10');
    expect(continuos).not.toContain('Sem previsão de remessa');
    http.verify();
  });

  it('Avisos recentes mostra os três últimos, destaca os não lidos e marca como lidos', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    const agora = new Date().toISOString();
    http.expectOne('/api/avisos').flush(
      [4, 3, 2, 1].map((id) => ({
        id,
        tipo: 'MEDICAMENTO_DISPONIVEL',
        titulo: `Aviso ${id}`,
        texto: 'Metformina chegou na sua UBS.',
        lido: id < 3,
        criadoEm: agora,
      })),
    );
    http.expectOne('/api/ubs').flush([]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    // o contador vem do menu; aqui ele é preenchido direto no serviço
    const avisos = TestBed.inject(AvisosService);
    avisos.contarNaoLidos();
    http.expectOne('/api/avisos/nao-lidos/total').flush({ naoLidos: 2 });
    fixture.detectChanges();

    const card = () =>
      (fixture.nativeElement as HTMLElement).querySelector('[aria-labelledby="titulo-avisos"]')!;
    const itens = Array.from(card().querySelectorAll('li'));
    expect(itens.map((li) => li.querySelector('p')!.textContent!.trim())).toEqual([
      'Aviso 4 (não lido)',
      'Aviso 3 (não lido)',
      'Aviso 2',
    ]);
    expect(itens[0].textContent).toContain('Hoje,');

    const marcar = Array.from(card().querySelectorAll('button')).find((b) =>
      b.textContent!.includes('Marcar como lidos'),
    )!;
    marcar.click();
    http.expectOne({ method: 'PATCH', url: '/api/avisos/lidas' }).flush({ atualizados: 2 });
    fixture.detectChanges();

    expect(card().querySelectorAll('li[data-lido="false"]').length).toBe(0);
    expect(card().textContent).not.toContain('Marcar como lidos');
    http.verify();
  });

  it('Avisos recentes mostra o erro e deixa tentar de novo', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    http.expectOne('/api/avisos').flush(null, { status: 500, statusText: 'Erro' });
    http.expectOne('/api/ubs').flush([]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    fixture.detectChanges();

    const card = (fixture.nativeElement as HTMLElement).querySelector(
      '[aria-labelledby="titulo-avisos"]',
    )!;
    expect(card.textContent).toContain('Não foi possível carregar os avisos.');
    card.querySelector('button')!.click();
    http.expectOne('/api/avisos').flush([]);
    fixture.detectChanges();
    expect(card.textContent).toContain('Você ainda não tem avisos.');
    http.verify();
  });
});
