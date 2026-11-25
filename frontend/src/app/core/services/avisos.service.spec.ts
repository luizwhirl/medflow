import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Aviso, quandoAconteceu, tomDoAviso } from '../models/aviso.model';
import { AvisosService } from './avisos.service';

const APARELHO = '15da7427-97f6-4bf1-9d99-004af964e951';

const aviso = (id: number, lido = false, extra: Partial<Aviso> = {}): Aviso => ({
  id,
  tipo: 'MEDICAMENTO_DISPONIVEL',
  titulo: 'Medicamento disponível',
  texto: 'O medicamento Metformina está disponível na unidade UBS Clima Bom.',
  lido,
  criadoEm: '2026-11-24T10:00:00Z',
  ...extra,
});

describe('tomDoAviso e quandoAconteceu', () => {
  it('usa alerta para dados atrasados e disponível para o resto', () => {
    expect(tomDoAviso({ tipo: 'UBS_DADOS_ATRASADOS' })).toBe('alerta');
    expect(tomDoAviso({ tipo: 'MEDICAMENTO_DISPONIVEL' })).toBe('disponivel');
  });

  it('formata o tempo de forma curta', () => {
    const agora = Date.parse('2026-11-23T12:00:00Z');
    expect(quandoAconteceu('2026-11-23T11:59:40Z', agora)).toBe('agora');
    expect(quandoAconteceu('2026-11-23T11:20:00Z', agora)).toBe('há 40 min');
    expect(quandoAconteceu('2026-11-23T09:00:00Z', agora)).toBe('há 3 h');
    expect(quandoAconteceu('2026-11-22T10:00:00Z', agora)).toBe('ontem');
    expect(quandoAconteceu('2026-11-19T12:00:00Z', agora)).toBe('há 4 dias');
    expect(quandoAconteceu(undefined)).toBe('');
  });
});

describe('AvisosService', () => {
  let http: HttpTestingController;

  function iniciar(): AvisosService {
    localStorage.setItem('medflow.dispositivo', APARELHO);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AvisosService);
  }

  function responder(avisos: Aviso[], naoLidos: number): void {
    const lista = http.expectOne({ method: 'GET', url: '/api/avisos' });
    const total = http.expectOne('/api/avisos/nao-lidos/total');
    expect(lista.request.headers.get('X-Dispositivo-Id')).toBe(APARELHO);
    expect(total.request.headers.get('X-Dispositivo-Id')).toBe(APARELHO);
    lista.flush(avisos);
    total.flush({ naoLidos });
  }

  afterEach(() => {
    http.verify();
    localStorage.removeItem('medflow.dispositivo');
  });

  it('pede os avisos do aparelho e usa o total de não lidos da API', () => {
    const service = iniciar();
    expect(service.estado()).toEqual({ tipo: 'carregando' });
    // O total conta também os avisos além dos 50 da lista.
    responder([aviso(2), aviso(1, true)], 60);

    expect(service.avisos().map((a) => a.id)).toEqual([2, 1]);
    expect(service.naoLidos()).toBe(60);
  });

  it('marca todos como lidos na API e zera o contador', () => {
    const service = iniciar();
    responder([aviso(2), aviso(1)], 2);

    service.marcarTodosComoLidos();
    const patch = http.expectOne({ method: 'PATCH', url: '/api/avisos/lidas' });
    expect(patch.request.headers.get('X-Dispositivo-Id')).toBe(APARELHO);
    patch.flush({ atualizados: 2 });

    expect(service.naoLidos()).toBe(0);
    expect(service.avisos().every((a) => a.lido)).toBeTrue();
  });

  it('se marcar como lidos falhar, mantém como estava', () => {
    const service = iniciar();
    responder([aviso(1)], 1);

    service.marcarTodosComoLidos();
    http
      .expectOne({ method: 'PATCH', url: '/api/avisos/lidas' })
      .flush(null, { status: 500, statusText: 'Erro' });

    expect(service.naoLidos()).toBe(1);
  });

  it('tenta de novo após erro', () => {
    const service = iniciar();
    http.expectOne({ method: 'GET', url: '/api/avisos' }).error(new ProgressEvent('error'));
    http.expectOne('/api/avisos/nao-lidos/total');
    expect(service.estado()).toEqual({ tipo: 'erro' });

    service.recarregar();
    responder([], 0);
    expect(service.estado()).toEqual({ tipo: 'ok', avisos: [], naoLidos: 0 });
  });
});
