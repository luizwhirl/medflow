import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { distanciaKm, formatarDistancia } from '../../core/geo';
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

describe('distanciaKm', () => {
  it('1 grau de latitude vale cerca de 111,19 km', () => {
    expect(distanciaKm({ latitude: 0, longitude: 0 }, { latitude: 1, longitude: 0 })).toBeCloseTo(
      111.19,
      1,
    );
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
});
