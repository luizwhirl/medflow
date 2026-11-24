import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Aviso, formatarPrevisao, quandoAconteceu, textoDoAviso } from '../models/aviso.model';
import { AvisosService } from './avisos.service';
import { PreferenciasService } from './preferencias.service';

const base = {
  ubsId: 2,
  ubsNome: 'UBS Clima Bom',
  medicamentoId: 1,
  medicamentoNome: 'Metformina',
};
const aviso = (id: string, extra: Partial<Aviso> = {}): Aviso =>
  ({ id, tipo: 'DISPONIVEL', ...base, ...extra }) as Aviso;

describe('textoDoAviso', () => {
  it('monta título, detalhe, tom e link de cada tipo', () => {
    expect(textoDoAviso(aviso('d', { medicamentoConcentracao: '850 mg', quantidade: 30 }))).toEqual(
      {
        titulo: 'Metformina 850 mg disponível',
        detalhe: '~30 un. na UBS Clima Bom. Retire no horário de funcionamento.',
        tom: 'disponivel',
        link: ['/medicamentos', 1],
      },
    );

    const remessa = textoDoAviso(
      aviso('r', {
        tipo: 'REMESSA',
        etapa: 'EM_TRANSPORTE',
        quantidade: 40,
        previsaoChegada: '2026-11-26',
      }),
    );
    expect(remessa.titulo).toBe('Metformina a caminho');
    expect(remessa.detalhe).toBe('Remessa de 40 un. para a UBS Clima Bom. Previsão: qui, 26/11.');
    expect(remessa.tom).toBe('info');

    const sinc = textoDoAviso(aviso('s', { tipo: 'SINCRONIZACAO_ATRASADA' }));
    expect(sinc.detalhe).toBe(
      'A UBS Clima Bom ainda não sincronizou o estoque. Ligue antes de ir.',
    );
    expect(sinc.link).toEqual(['/ubs', 2]);
  });

  it('descreve o atraso da sincronização em horas e depois em dias', () => {
    const ha = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString();
    const detalhe = (h: number) =>
      textoDoAviso(aviso('s', { tipo: 'SINCRONIZACAO_ATRASADA', ocorridoEm: ha(h) })).detalhe;
    expect(detalhe(30)).toContain('sem sincronizar há 30 h');
    expect(detalhe(24 * 12)).toContain('sem sincronizar há 12 dias');
  });
});

describe('formatarPrevisao e quandoAconteceu', () => {
  it('formatam a previsão sem mudar o dia e o tempo de forma curta', () => {
    expect(formatarPrevisao('2026-11-23')).toBe('seg, 23/11');
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

  function iniciar(
    ubsReferenciaId: number | null,
    remediosContinuos: number[],
    lidos: string[] = [],
  ) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId, remediosContinuos, buscasRecentes: [] }),
    );
    localStorage.setItem('medflow.avisos-lidos', JSON.stringify(lidos));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(AvisosService);
    service.estado(); // assina a carga
    TestBed.tick();
    return service;
  }

  afterEach(() => http.verify());

  it('sem UBS de referência não chama a API', () => {
    const service = iniciar(null, [1]);
    expect(service.estado()).toEqual({ tipo: 'sem-ubs' });
  });

  it('pede os avisos da UBS e dos remédios e conta os não lidos', () => {
    const service = iniciar(2, [1, 3], ['a']);
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1,3').flush([aviso('a'), aviso('b')]);

    expect(service.avisos().map((a) => [a.id, a.lido])).toEqual([
      ['a', true],
      ['b', false],
    ]);
    expect(service.naoLidos()).toBe(1);
  });

  it('marca como lido, guarda no navegador e esquece lidos que sumiram', () => {
    const service = iniciar(2, [], ['antigo']);
    http.expectOne('/api/avisos?ubsId=2').flush([aviso('a'), aviso('b')]);
    expect(JSON.parse(localStorage.getItem('medflow.avisos-lidos')!)).toEqual([]);

    service.marcarComoLido('a');
    expect(service.naoLidos()).toBe(1);
    service.marcarTodosComoLidos();
    expect(service.naoLidos()).toBe(0);
    expect(JSON.parse(localStorage.getItem('medflow.avisos-lidos')!)).toEqual(['a', 'b']);
  });

  it('recarrega ao trocar a UBS de referência e tenta de novo após erro', () => {
    const service = iniciar(2, [1]);
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1').error(new ProgressEvent('error'));
    expect(service.estado()).toEqual({ tipo: 'erro' });

    service.recarregar();
    TestBed.tick();
    http.expectOne('/api/avisos?ubsId=2&medicamentoIds=1').flush([]);
    expect(service.estado()).toEqual({ tipo: 'ok', avisos: [] });

    TestBed.inject(PreferenciasService).definirUbsReferencia(5);
    TestBed.tick();
    http.expectOne('/api/avisos?ubsId=5&medicamentoIds=1').flush([]);
  });
});
