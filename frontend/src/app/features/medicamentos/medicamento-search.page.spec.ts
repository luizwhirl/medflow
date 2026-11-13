import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
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
  formaFarmaceutica: 'Comprimido',
  categoria: 'Diabetes',
  rename: true,
};

const ubs = { id: 3, nome: 'UBS Clima Bom', bairro: 'Clima Bom' } as Ubs;

describe('Timeline', () => {
  it('marca como concluídas as etapas até a atual', () => {
    const fixture = TestBed.createComponent(Timeline);
    fixture.componentRef.setInput('etapas', ['A', 'B', 'C']);
    fixture.componentRef.setInput('atual', 1);
    fixture.detectChanges();
    const itens = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('li'));
    expect(itens.map((li) => li.textContent!.includes('(concluída)'))).toEqual([true, true, false]);
    expect(itens[1].getAttribute('aria-current')).toBe('step');
  });
});

describe('MedicamentoSearchPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('filtra pela categoria escolhida no chip', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush(['Diabetes', 'Hipertensão']);
    tick(250);
    http.expectOne('/api/medicamentos').flush([metformina]);
    fixture.detectChanges();

    const chip = (fixture.nativeElement as HTMLElement).querySelector(
      'app-chip button',
    ) as HTMLButtonElement;
    chip.click();
    fixture.detectChanges();
    tick(250);

    const req = http.expectOne((r) => r.url === '/api/medicamentos');
    expect(req.request.params.get('categoria')).toBe('Diabetes');
    req.flush([metformina]);
    http.verify();
  }));

  it('com medicamento selecionado, mostra a remessa concluída quando há unidade disponível', fakeAsync(() => {
    const fixture = TestBed.createComponent(MedicamentoSearchPage);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
    http.expectOne('/api/medicamentos/categorias').flush([]);
    http.expectOne('/api/medicamentos/1').flush(metformina);
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([{ ubs, status: 'DISPONIVEL', quantidade: 40, atualizadoEm: '' }]);
    tick(250);
    http.expectOne('/api/medicamentos').flush([metformina]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const etapas = el.querySelectorAll('app-timeline li');
    expect(etapas.length).toBe(5);
    expect(etapas[4].textContent).toContain('(concluída)');
    expect(el.textContent).toContain('UBS Clima Bom');
    expect((el.querySelector('button[disabled]') as HTMLElement).textContent).toContain('Me avise');
    http.verify();
  }));
});
