import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { compararDistancia, formatarDistancia } from '../../core/geo';
import { Medicamento } from '../../core/models/medicamento.model';
import { Ubs } from '../../core/models/ubs.model';
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

describe('PreferenciasService', () => {
  beforeEach(() => localStorage.removeItem('medflow.preferencias'));

  it('alterna remédios e persiste no navegador', () => {
    const prefs = TestBed.inject(PreferenciasService);
    prefs.alternarRemedioContinuo(3);
    prefs.alternarRemedioContinuo(5);
    prefs.alternarRemedioContinuo(3);
    prefs.definirUbsReferencia(7);

    expect(prefs.remediosContinuos()).toEqual([5]);
    expect(JSON.parse(localStorage.getItem('medflow.preferencias')!)).toEqual({
      ubsReferenciaId: 7,
      remediosContinuos: [5],
      buscasRecentes: [],
      boasVindasConcluidas: false,
    });
  });
});

describe('HomePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 2, remediosContinuos: [1], buscasRecentes: ['Insulina'] }),
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

    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush(['Diabetes', 'Hipertensão']);
    http.expectOne('/api/medicamentos/1').flush(metformina);
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

    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
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

    http.expectOne('/api/ubs').flush([ubs(1, 'UBS Centro'), ubs(2, 'UBS Clima Bom')]);
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
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
});
