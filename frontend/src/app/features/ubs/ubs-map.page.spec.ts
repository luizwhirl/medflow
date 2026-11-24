import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { Medicamento } from '../../core/models/medicamento.model';
import { UbsProxima } from '../../core/models/ubs.model';
import { Coordenadas } from '../../core/geo';
import { Mapa, PinoMapa } from '../../shared/components/mapa';
import { UbsMapPage } from './ubs-map.page';

/** Resposta paginada da API (primeira página com todos os itens). */
const pagina = <T>(itens: T[]) => ({
  itens,
  pagina: 0,
  tamanho: 20,
  totalItens: itens.length,
  totalPaginas: itens.length ? 1 : 0,
});

/** Substitui o Leaflet para que os testes não baixem tiles da internet. */
@Component({ selector: 'app-mapa', template: '' })
class MapaFalso {
  readonly pinos = input<PinoMapa[]>([]);
  readonly centro = input<Coordenadas | null>(null);
  readonly raioKm = input<number | null>(null);
  readonly selecionado = input<number | null>(null);
  readonly selecionar = output<number>();
}

/** UBS como a busca por proximidade devolve, a `km` quilômetros da origem. */
const ubs = (id: number, nome: string, km: number): UbsProxima => ({
  id,
  cnes: `${id}`,
  nome,
  tipo: 'UBS',
  bairro: 'Centro',
  endereco: 'Rua A',
  telefone: null,
  horario: null,
  latitude: km / 111.19,
  longitude: 0,
  ultimaSincronizacao: null,
  distanciaKm: km,
});

/** Busca por proximidade a partir da posição dos testes (0, 0). */
const proximas = (raioKm: number) => `/api/ubs?lat=0&lng=0&raioKm=${raioKm}`;

const med = (id: number, nome: string) => ({ id, nome, concentracao: '' }) as Medicamento;

describe('UbsMapPage', () => {
  let http: HttpTestingController;

  let meus: number[] = [];

  /** Os remédios de uso contínuo são os da lista de desejos do aparelho. */
  function responderLista() {
    http
      .expectOne('/api/lista-desejos')
      .flush(
        meus.map((medicamentoId, i) => ({ id: 10 + i, ubsId: 2, medicamentoId, notificar: false })),
      );
  }

  function preparar(remediosContinuos: number[], localizacao = true) {
    meus = remediosContinuos;
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 2, buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok, erro) =>
      localizacao
        ? ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition)
        : erro!({ code: 1 } as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.overrideComponent(UbsMapPage, {
      remove: { imports: [Mapa] },
      add: { imports: [MapaFalso] },
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => localStorage.removeItem('medflow.preferencias'));

  function linhas(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('[aria-label="Unidades no mapa"] button')).map((b) =>
      Array.from(b.querySelectorAll(':scope > span'))
        .map((span) => span.textContent!.replace(/\s+/g, ' ').trim())
        .join(' '),
    );
  }

  it('pede à API as UBS do raio e numera na ordem e com a distância que ela devolve', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.46)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(linhas(el)).toEqual(['1 Perto 500 m · Sua UBS', '2 Meio 2,5 km · Centro']);

    const umKm = Array.from(el.querySelectorAll('app-chip button')).find(
      (b) => b.textContent!.trim() === '1 km',
    ) as HTMLButtonElement;
    umKm.click();
    fixture.detectChanges();
    http.expectOne(proximas(1)).flush([ubs(2, 'Perto', 0.5)]);
    fixture.detectChanges();
    expect(linhas(el)).toEqual(['1 Perto 500 m · Sua UBS']);
    http.verify();
  }));

  it('sem raio escolhido, deixa a API usar o raio padrão', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const seisKm = Array.from(el.querySelectorAll('app-chip button')).find(
      (b) => b.textContent!.trim() === '6 km',
    ) as HTMLButtonElement;
    seisKm.click();
    fixture.detectChanges();
    http.expectOne('/api/ubs?lat=0&lng=0').flush([ubs(2, 'Perto', 0.5), ubs(9, 'Longe', 15)]);
    fixture.detectChanges();
    expect(linhas(el)).toEqual(['1 Perto 500 m · Sua UBS', '2 Longe 15,0 km · Centro']);
    http.verify();
  }));

  it('"Abertas agora" deixa só as UBS abertas e os cartões dizem até quando', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([
      // aberta, fechada (fora do horário ou fim de semana) e sem horário cadastrado
      {
        ...ubs(2, 'Perto', 0.5),
        abertaAgora: true,
        fechaAs: '17:00',
        fechaEm: '2999-01-01T17:00:00-03:00',
      },
      { ...ubs(3, 'Meio', 2.5), abertaAgora: false, fechaAs: null, fechaEm: null },
      { ...ubs(4, 'Longe', 4), abertaAgora: null },
    ]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(linhas(el)).toEqual([
      '1 Perto 500 m · Sua UBS Aberta até 17h',
      '2 Meio 2,5 km · Centro Fechada agora',
      '3 Longe 4,0 km · Centro',
    ]);
    expect(el.querySelector('article')!.textContent).toContain('Aberta até 17h');

    const abertas = Array.from(el.querySelectorAll('app-chip button')).find(
      (b) => b.textContent!.trim() === 'Abertas agora',
    ) as HTMLButtonElement;
    abertas.click();
    fixture.detectChanges();
    // filtra no navegador, sem nova chamada; a UBS sem horário também sai
    expect(linhas(el)).toEqual(['1 Perto 500 m · Sua UBS Aberta até 17h']);

    abertas.click();
    fixture.detectChanges();
    expect(linhas(el).length).toBe(3);
    http.verify();
  }));

  it('sem localização, lista todas as UBS por nome e sem distância', fakeAsync(() => {
    preparar([], false);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    const { distanciaKm: _a, ...centro } = ubs(1, 'Centro', 0);
    const { distanciaKm: _b, ...perto } = ubs(2, 'Perto', 0);
    responderLista();
    fixture.detectChanges();
    http.expectOne('/api/ubs').flush([centro, perto]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    fixture.detectChanges();

    expect(linhas(fixture.nativeElement)).toEqual(['1 Centro Centro', '2 Perto Sua UBS']);
    http.verify();
  }));

  it('resume quantos dos meus remédios cada UBS tem e detalha a selecionada', fakeAsync(() => {
    preparar([1, 4]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    http.expectOne('/api/medicamentos/1').flush(med(1, 'Metformina'));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      { ubs: ubs(2, 'Perto', 0.5), status: 'EM_FALTA', quantidade: 0, atualizadoEm: '' },
      { ubs: ubs(3, 'Meio', 2.5), status: 'DISPONIVEL', quantidade: 120, atualizadoEm: '' },
    ]);
    http.expectOne('/api/medicamentos/4').flush(med(4, 'Losartana'));
    http.expectOne('/api/medicamentos/4/disponibilidade').flush([
      { ubs: ubs(2, 'Perto', 0.5), status: 'DISPONIVEL', quantidade: 80, atualizadoEm: '' },
      { ubs: ubs(3, 'Meio', 2.5), status: 'DISPONIVEL', quantidade: 9, atualizadoEm: '' },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(linhas(el)).toEqual([
      '1 Perto 500 m · Sua UBS 1 de 2 remédios',
      '2 Meio 2,5 km · Centro 2 de 2 remédios',
    ]);

    (el.querySelectorAll('[aria-label="Unidades no mapa"] button')[1] as HTMLElement).click();
    fixture.detectChanges();
    const cartao = el.querySelector('article')!.textContent!.replace(/\s+/g, ' ');
    expect(cartao).toContain('Meio');
    expect(cartao).toContain('Metformina · ~120 un.');
    http.verify();
  }));

  it('trocar para "Nenhum" desliga o filtro "Com meus remédios"', fakeAsync(() => {
    preparar([1]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    http.expectOne('/api/medicamentos/1').flush(med(1, 'Metformina'));
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([{ ubs: ubs(3, 'Meio', 2.5), status: 'DISPONIVEL', quantidade: 5, atualizadoEm: '' }]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const chip = Array.from(el.querySelectorAll('app-chip button')).find((b) =>
      b.textContent!.includes('Com meus remédios'),
    ) as HTMLButtonElement;
    chip.click();
    fixture.detectChanges();
    expect(linhas(el).length).toBe(1);

    const select = el.querySelector('select')!;
    select.value = '';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(linhas(el).length).toBe(2);
    http.verify();
  }));

  it('busca o remédio na API conforme a pessoa digita e mantém o escolhido na lista', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([med(1, 'Metformina')]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const opcoes = () =>
      Array.from(el.querySelectorAll('select option')).map((o) => o.textContent!.trim());
    const select = el.querySelector('select')!;
    select.value = '1';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/1').flush(med(1, 'Metformina'));
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([]);
    fixture.detectChanges();

    const campo = el.querySelector('input[type="search"]') as HTMLInputElement;
    campo.value = 'losa';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    tick(249);
    http.expectNone('/api/medicamentos?busca=losa&tamanho=100');
    tick(1);
    http.expectOne('/api/medicamentos?busca=losa&tamanho=100').flush(pagina([med(4, 'Losartana')]));
    fixture.detectChanges();
    expect(opcoes()).toEqual(['Nenhum (só as unidades)', 'Metformina', 'Losartana']);

    campo.value = 'xyz';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    tick(250);
    http.expectOne('/api/medicamentos?busca=xyz&tamanho=100').flush(pagina([]));
    select.value = '';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(opcoes()).toEqual(['Nenhum (só as unidades)', 'Nenhum remédio encontrado']);
    http.verify();
  }));

  it('repassa as UBS ao mapa e seleciona a que for clicada nele, sem carregar o Leaflet', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    responderLista();
    fixture.detectChanges();
    http.expectOne(proximas(6)).flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos?tamanho=100').flush(pagina([]));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.leaflet-container')).toBeNull();

    const mapa = fixture.debugElement.query(By.directive(MapaFalso)).componentInstance as MapaFalso;
    expect(mapa.pinos().map((p) => [p.numero, p.rotulo])).toEqual([
      [1, 'Perto'],
      [2, 'Meio'],
    ]);
    expect(mapa.centro()).toEqual({ latitude: 0, longitude: 0 });

    mapa.selecionar.emit(3);
    fixture.detectChanges();
    expect(mapa.selecionado()).toBe(3);
    expect(el.querySelector('article')!.textContent).toContain('Meio');
    http.verify();
  }));
});
