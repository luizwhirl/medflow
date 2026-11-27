import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DispositivoService } from './dispositivo.service';
import { ListaDesejosService } from './lista-desejos.service';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('DispositivoService', () => {
  beforeEach(() => localStorage.removeItem('medflow.dispositivo'));

  it('gera um UUID v4, guarda no navegador e reaproveita', () => {
    const id = TestBed.inject(DispositivoService).identificador();
    expect(id).toMatch(UUID);
    expect(localStorage.getItem('medflow.dispositivo')).toBe(id);

    TestBed.resetTestingModule();
    expect(TestBed.inject(DispositivoService).identificador()).toBe(id);
  });

  it('troca um identificador salvo que não seja UUID', () => {
    localStorage.setItem('medflow.dispositivo', 'adulterado');
    const id = TestBed.inject(DispositivoService).identificador();
    expect(id).toMatch(UUID);
    expect(id).not.toBe('adulterado');
  });

  it('monta o UUID v4 sem crypto.randomUUID (HTTP fora de localhost)', () => {
    spyOnProperty(window, 'crypto').and.returnValue({
      getRandomValues: (b: Uint8Array) => b.fill(0xff),
    } as unknown as Crypto);
    expect(TestBed.inject(DispositivoService).identificador()).toBe(
      'ffffffff-ffff-4fff-bfff-ffffffffffff',
    );
  });
});

describe('ListaDesejosService', () => {
  let http: HttpTestingController;
  let service: ListaDesejosService;
  const aparelho = '3f1c2b7e-8a4d-4e1f-9b2c-5d6e7f8a9b0c';

  beforeEach(() => {
    localStorage.setItem('medflow.dispositivo', aparelho);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ListaDesejosService);
  });

  afterEach(() => http.verify());

  it('envia o identificador do aparelho em todas as chamadas', () => {
    service.listar().subscribe();
    service.adicionar(2, 3).subscribe();
    service.definirAviso(10, false).subscribe();
    service.remover(10).subscribe();

    const listar = http.expectOne({ method: 'GET', url: '/api/lista-desejos' });
    const adicionar = http.expectOne({ method: 'POST', url: '/api/lista-desejos' });
    const aviso = http.expectOne({ method: 'PATCH', url: '/api/lista-desejos/10' });
    const remover = http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/10' });

    for (const req of [listar, adicionar, aviso, remover]) {
      expect(req.request.headers.get('X-Dispositivo-Id')).toBe(aparelho);
    }
    expect(adicionar.request.body).toEqual({ ubsId: 2, medicamentoId: 3, notificar: true });
    expect(aviso.request.body).toEqual({ notificar: false });
  });
});
