import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Medicamento } from '../../core/models/medicamento.model';
import { Ubs } from '../../core/models/ubs.model';
import { UbsMapPage } from './ubs-map.page';

/** UBS a `km` quilômetros ao norte da origem (1° de latitude ≈ 111,19 km). */
const ubs = (id: number, nome: string, km: number): Ubs => ({
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
});

const med = (id: number, nome: string) => ({ id, nome, concentracao: '' }) as Medicamento;

describe('UbsMapPage', () => {
  let http: HttpTestingController;

  function preparar(remediosContinuos: number[]) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 2, remediosContinuos, buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok) =>
      ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
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

  it('numera as UBS dentro do raio, da mais próxima para a mais distante', fakeAsync(() => {
    preparar([]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    http
      .expectOne('/api/ubs')
      .flush([ubs(1, 'Longe', 8), ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos').flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(linhas(el)).toEqual(['1 Perto 500 m · Sua UBS', '2 Meio 2,5 km · Centro']); // 6 km

    const umKm = Array.from(el.querySelectorAll('app-chip button')).find(
      (b) => b.textContent!.trim() === '1 km',
    ) as HTMLButtonElement;
    umKm.click();
    fixture.detectChanges();
    expect(linhas(el).length).toBe(1);
  }));

  it('resume quantos dos meus remédios cada UBS tem e detalha a selecionada', fakeAsync(() => {
    preparar([1, 4]);
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/ubs').flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos').flush([]);
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
    http.expectOne('/api/ubs').flush([ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos').flush([]);
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
});
