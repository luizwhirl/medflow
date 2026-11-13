import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

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

describe('UbsMapPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok) =>
      ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function nomes(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('app-list li .font-semibold')).map((n) =>
      n.textContent!.trim(),
    );
  }

  it('lista só as UBS dentro do raio, da mais próxima para a mais distante', fakeAsync(() => {
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    http
      .expectOne('/api/ubs')
      .flush([ubs(1, 'Longe', 8), ubs(2, 'Perto', 0.5), ubs(3, 'Meio', 2.5)]);
    http.expectOne('/api/medicamentos').flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(nomes(el)).toEqual(['Perto', 'Meio']); // raio padrão 6 km

    (el.querySelectorAll('app-chip button')[0] as HTMLButtonElement).click(); // 1 km
    fixture.detectChanges();
    expect(nomes(el)).toEqual(['Perto']);
  }));

  it('mostra o status do medicamento escolhido em cada UBS', fakeAsync(() => {
    const fixture = TestBed.createComponent(UbsMapPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/ubs').flush([ubs(2, 'Perto', 0.5)]);
    http.expectOne('/api/medicamentos').flush([{ id: 1, nome: 'Metformina', concentracao: '' }]);
    fixture.detectChanges();

    const select = (fixture.nativeElement as HTMLElement).querySelector('select')!;
    select.value = '1';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([{ ubs: ubs(2, 'Perto', 0.5), status: 'RESERVADO', quantidade: 4, atualizadoEm: '' }]);
    fixture.detectChanges();

    const linha = (fixture.nativeElement as HTMLElement).querySelector('app-list li')!;
    expect(linha.textContent).toContain('Reservado');
    expect(linha.textContent).toContain('4 un.');
    http.verify();
  }));
});
