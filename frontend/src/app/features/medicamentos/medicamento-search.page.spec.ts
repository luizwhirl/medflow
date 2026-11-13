import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Medicamento } from '../../core/models/medicamento.model';
import { Ubs } from '../../core/models/ubs.model';
import { Timeline } from '../../shared/components/timeline';
import { MedicamentoSearchPage } from './medicamento-search.page';

const metformina: Medicamento = {
  id: 1,
  catmat: 'BR0267691',
  nome: 'Metformina',
  concentracao: '850 mg',
  formaFarmaceutica: 'comprimido',
  categoria: 'Diabetes',
  rename: true,
};

/** UBS a `km` quilômetros ao norte da origem (1° de latitude ≈ 111,19 km). */
const ubs = (id: number, nome: string, km: number) =>
  ({ id, nome, bairro: 'Centro', latitude: km / 111.19, longitude: 0 }) as Ubs;

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
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 1, remediosContinuos: [], buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok) =>
      ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.removeItem('medflow.preferencias'));

  function resultados(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('[aria-label="Resultados"] a')).map((a) =>
      a.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('conta só as UBS até 5 km e respeita "Só disponíveis"', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    tick(250);
    http
      .expectOne('/api/medicamentos')
      .flush([metformina, { ...metformina, id: 2, nome: 'Glibenclamida' }]);
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
      'Glibenclamida 850 mg comprimido Nenhuma UBS a até 5 km',
    ]);

    const soDisponiveis = Array.from(el.querySelectorAll('app-chip button')).find((b) =>
      b.textContent!.includes('Só disponíveis'),
    ) as HTMLButtonElement;
    soDisponiveis.click();
    fixture.detectChanges();
    expect(resultados(el).length).toBe(1);
    http.verify();
  }));

  it('filtra pela categoria escolhida no chip', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/medicamentos/categorias').flush(['Diabetes']);
    tick(250);
    http.expectOne('/api/medicamentos').flush([]);
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

  it('no detalhe, avisa a falta na UBS de referência e aponta onde tem', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
    flushMicrotasks();
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
    tick(250);
    http.expectOne('/api/medicamentos').flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const alerta = el.querySelector('[role="status"]')!.textContent!.replace(/\s+/g, ' ');
    expect(alerta).toContain('Em falta na UBS Benedito Bentes I');
    expect(alerta).toContain('a UBS Clima Bom (2,4 km) tem estoque');
    const onde = el.querySelector('[aria-labelledby="titulo-onde"]')!.textContent!;
    expect(onde.indexOf('UBS Clima Bom')).toBeLessThan(onde.indexOf('UBS Benedito Bentes I'));
    expect(onde).toContain('~120 un.');
    expect(el.querySelectorAll('app-timeline li').length).toBe(4);
    http.verify();
  }));

  it('não conclui a remessa quando o estoque está só em outra UBS', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http.expectOne('/api/medicamentos/1/disponibilidade').flush([
      {
        ubs: ubs(3, 'UBS Clima Bom', 2.4),
        status: 'DISPONIVEL',
        quantidade: 120,
        atualizadoEm: '',
      },
    ]);
    tick(250);
    http.expectOne('/api/medicamentos').flush([]);
    fixture.detectChanges();

    const etapas = (fixture.nativeElement as HTMLElement).querySelectorAll('app-timeline li');
    expect(Array.from(etapas).some((li) => li.textContent!.includes('(concluída)'))).toBeFalse();
    http.verify();
  }));
});
