import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Aviso } from '../models/aviso.model';
import { AvisosService } from './avisos.service';

const aparelho = '3f1c2b7e-8a4d-4e1f-9b2c-5d6e7f8a9b0c';

const aviso = (id: number, lido: boolean): Aviso => ({
  id,
  tipo: 'MEDICAMENTO_DISPONIVEL',
  titulo: 'Medicamento disponível',
  texto: 'Metformina chegou na sua UBS.',
  lido,
  criadoEm: '2026-11-24T15:00:00Z',
});

describe('AvisosService', () => {
  let http: HttpTestingController;
  let service: AvisosService;

  beforeEach(() => {
    localStorage.setItem('medflow.dispositivo', aparelho);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AvisosService);
  });

  afterEach(() => http.verify());

  it('lista e conta os avisos do aparelho pelo X-Dispositivo-Id', () => {
    service.listar().subscribe();
    service.contarNaoLidos();

    const lista = http.expectOne({ method: 'GET', url: '/api/avisos' });
    const total = http.expectOne({ method: 'GET', url: '/api/avisos/nao-lidos/total' });
    for (const req of [lista, total]) {
      expect(req.request.headers.get('X-Dispositivo-Id')).toBe(aparelho);
    }
    lista.flush([aviso(2, false), aviso(1, true)]);
    total.flush({ naoLidos: 7 });

    expect(service.avisos()?.map((a) => a.id)).toEqual([2, 1]);
    expect(service.naoLidos()).toBe(7);
  });

  it('marcar como lidos zera o contador e marca os avisos já carregados', () => {
    service.listar().subscribe();
    http.expectOne('/api/avisos').flush([aviso(2, false), aviso(1, true)]);
    service.contarNaoLidos();
    http.expectOne('/api/avisos/nao-lidos/total').flush({ naoLidos: 1 });

    service.marcarComoLidos().subscribe();
    const req = http.expectOne({ method: 'PATCH', url: '/api/avisos/lidas' });
    expect(req.request.headers.get('X-Dispositivo-Id')).toBe(aparelho);
    req.flush({ atualizados: 1 });

    expect(service.naoLidos()).toBe(0);
    expect(service.avisos()?.every((a) => a.lido)).toBeTrue();
  });

  it('falha na leitura deixa a lista como erro e o contador como estava', () => {
    service.contarNaoLidos();
    http.expectOne('/api/avisos/nao-lidos/total').flush({ naoLidos: 3 });

    service.listar().subscribe({ error: () => undefined });
    http.expectOne('/api/avisos').flush(null, { status: 500, statusText: 'Erro' });
    service.contarNaoLidos();
    http.expectOne('/api/avisos/nao-lidos/total').error(new ProgressEvent('error'));

    expect(service.avisos()).toBeNull();
    expect(service.naoLidos()).toBe(3);
  });
});
