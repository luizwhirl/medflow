import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MedicamentoService } from './medicamento.service';

describe('MedicamentoService.buscar', () => {
  let http: HttpTestingController;
  let service: MedicamentoService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MedicamentoService);
  });

  afterEach(() => http.verify());

  it('manda as alternativas da voz como busca repetida, no máximo três, numa chamada só', () => {
    service.buscar('losartana', '', undefined, ['losartan', 'lozartana', 'losa']).subscribe();
    const req = http.expectOne((r) => r.url === '/api/medicamentos');
    expect(req.request.params.getAll('busca')).toEqual(['losartana', 'losartan', 'lozartana']);
    req.flush({ itens: [], pagina: 0, tamanho: 20, totalItens: 0, totalPaginas: 0 });
  });

  it('sem termo, não manda busca nem as alternativas', () => {
    service.buscar('', 'Diabetes', undefined, ['losartan']).subscribe();
    const req = http.expectOne((r) => r.url === '/api/medicamentos');
    expect(req.request.params.has('busca')).toBeFalse();
    req.flush({ itens: [], pagina: 0, tamanho: 20, totalItens: 0, totalPaginas: 0 });
  });
});
