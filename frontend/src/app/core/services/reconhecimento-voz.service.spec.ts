import { DestroyRef, EnvironmentInjector, createEnvironmentInjector } from '@angular/core';
import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';

import type {
  AmbienteReconhecimentoVoz,
  EventoErroReconhecimento,
  EventoResultadoReconhecimento,
  ReconhecimentoVoz,
  ResultadoReconhecimento,
} from '../types/speech-recognition';

import {
  AMBIENTE_RECONHECIMENTO_VOZ,
  ErroVoz,
  ReconhecimentoVozService,
  ResultadoVoz,
} from './reconhecimento-voz.service';

class ReconhecimentoFalso implements ReconhecimentoVoz {
  static instancias: ReconhecimentoFalso[] = [];

  lang = '';
  continuous = true;
  interimResults = false;
  maxAlternatives = 1;

  onstart: (() => void) | null = null;
  onspeechend: (() => void) | null = null;
  onresult: ((evento: EventoResultadoReconhecimento) => void) | null = null;
  onnomatch: (() => void) | null = null;
  onerror: ((evento: EventoErroReconhecimento) => void) | null = null;
  onend: (() => void) | null = null;

  start = jasmine.createSpy('start');
  stop = jasmine.createSpy('stop');
  abort = jasmine.createSpy('abort');

  constructor() {
    ReconhecimentoFalso.instancias.push(this);
  }

  emitirResultado(final: boolean, ...textos: string[]): void {
    const resultado: ResultadoReconhecimento = Object.assign(
      textos.map((transcript, indice) => ({
        transcript,
        confidence: 1 - indice * 0.1,
      })),
      { isFinal: final },
    );

    this.onresult?.({
      resultIndex: 0,
      results: [resultado],
    });
  }

  emitirErro(error: string): void {
    this.onerror?.({ error });
  }

  encerrar(): void {
    this.onend?.();
  }
}

describe('ReconhecimentoVozService', () => {
  beforeEach(() => {
    ReconhecimentoFalso.instancias = [];
  });

  function criarServico(
    ambiente: AmbienteReconhecimentoVoz | null = {
      isSecureContext: true,
      SpeechRecognition: ReconhecimentoFalso,
    },
  ): ReconhecimentoVozService {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AMBIENTE_RECONHECIMENTO_VOZ,
          useValue: ambiente,
        },
      ],
    });

    return TestBed.inject(ReconhecimentoVozService);
  }

  function reconhecimentoAtual(): ReconhecimentoFalso {
    return ReconhecimentoFalso.instancias[ReconhecimentoFalso.instancias.length - 1];
  }

  it('configura o reconhecimento em português do Brasil', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    expect(service.suportado).toBeTrue();
    expect(reconhecimento.lang).toBe('pt-BR');
    expect(reconhecimento.continuous).toBeFalse();
    expect(reconhecimento.interimResults).toBeTrue();
    expect(reconhecimento.maxAlternatives).toBe(3);
    expect(reconhecimento.start).toHaveBeenCalledTimes(1);

    reconhecimento.onstart?.();
    expect(service.estado()).toBe('ouvindo');

    service.cancelar();
    expect(await promessa).toBeNull();
  });

  it('atualiza o texto parcial e entrega o resultado final', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    reconhecimento.emitirResultado(false, 'losar');

    expect(service.parcial()).toBe('losar');
    expect(service.estado()).toBe('ouvindo');

    reconhecimento.onspeechend?.();
    expect(service.estado()).toBe('processando');

    reconhecimento.emitirResultado(true, 'Losartana', 'Losartan', 'Losartana potássica');

    expect(service.parcial()).toBe('');

    reconhecimento.encerrar();

    expect(await promessa).toEqual({
      texto: 'Losartana',
      alternativas: ['Losartan', 'Losartana potássica'],
    });

    expect(service.estado()).toBe('inativo');
    expect(service.erro()).toBeNull();
  });

  it('remove alternativas duplicadas e vazias', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    reconhecimento.emitirResultado(true, ' Dipirona ', 'Dipirona', '', 'Dipirona sódica');

    reconhecimento.encerrar();

    expect(await promessa).toEqual({
      texto: 'Dipirona',
      alternativas: ['Dipirona sódica'],
    });
  });

  const erros: [string, ErroVoz][] = [
    ['not-allowed', 'sem-permissao'],
    ['service-not-allowed', 'sem-permissao'],
    ['no-speech', 'sem-fala'],
    ['audio-capture', 'sem-microfone'],
    ['network', 'sem-rede'],
    ['language-not-supported', 'idioma-nao-suportado'],
    ['bad-grammar', 'outro'],
    ['phrases-not-supported', 'outro'],
    ['desconhecido', 'outro'],
  ];

  for (const [codigo, esperado] of erros) {
    it(`mapeia ${codigo} para ${esperado}`, async () => {
      const service = criarServico();
      const promessa = service.ouvir();

      reconhecimentoAtual().emitirErro(codigo);

      expect(await promessa).toBeNull();
      expect(service.erro()).toBe(esperado);
      expect(service.estado()).toBe('erro');
      expect(service.parcial()).toBe('');
    });
  }

  it('trata aborted como cancelamento sem erro', async () => {
    const service = criarServico();
    const promessa = service.ouvir();

    reconhecimentoAtual().emitirErro('aborted');

    expect(await promessa).toBeNull();
    expect(service.estado()).toBe('inativo');
    expect(service.erro()).toBeNull();
  });

  it('trata nomatch como ausência de fala reconhecível', async () => {
    const service = criarServico();
    const promessa = service.ouvir();

    reconhecimentoAtual().onnomatch?.();

    expect(await promessa).toBeNull();
    expect(service.erro()).toBe('sem-fala');
  });

  it('resolve null quando a escuta termina sem resultado', async () => {
    const service = criarServico();
    const promessa = service.ouvir();

    reconhecimentoAtual().encerrar();

    expect(await promessa).toBeNull();
    expect(service.erro()).toBe('sem-fala');
  });

  it('parar utiliza stop e preserva o resultado final', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    service.parar();
    service.parar();

    expect(reconhecimento.stop).toHaveBeenCalledTimes(1);
    expect(reconhecimento.abort).not.toHaveBeenCalled();
    expect(service.estado()).toBe('processando');

    reconhecimento.emitirResultado(true, 'Metformina');
    reconhecimento.encerrar();

    expect(await promessa).toEqual({
      texto: 'Metformina',
      alternativas: [],
    });
  });

  it('cancelar utiliza abort e descarta o texto parcial', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    reconhecimento.emitirResultado(false, 'Met');

    service.cancelar();

    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
    expect(reconhecimento.stop).not.toHaveBeenCalled();
    expect(await promessa).toBeNull();
    expect(service.estado()).toBe('inativo');
    expect(service.parcial()).toBe('');
    expect(service.erro()).toBeNull();
  });

  it('cancela a escuta anterior e ignora eventos atrasados', async () => {
    const service = criarServico();

    const primeira = service.ouvir();
    const anterior = reconhecimentoAtual();
    const eventoAtrasado = anterior.onerror;

    const segunda = service.ouvir();
    const atual = reconhecimentoAtual();

    expect(await primeira).toBeNull();
    expect(anterior.abort).toHaveBeenCalledTimes(1);

    eventoAtrasado?.({ error: 'network' });

    expect(service.estado()).toBe('ouvindo');
    expect(service.erro()).toBeNull();

    atual.emitirResultado(true, 'Dipirona');
    atual.encerrar();

    expect(await segunda).toEqual({
      texto: 'Dipirona',
      alternativas: [],
    });
  });

  it('limita a escuta a dez segundos', fakeAsync(() => {
    const service = criarServico();

    let resposta: ResultadoVoz | null | undefined;

    void service.ouvir().then((resultado) => {
      resposta = resultado;
    });

    const reconhecimento = reconhecimentoAtual();

    // Simula oito segundos aguardando a autorização do microfone.
    tick(8_000);
    expect(reconhecimento.stop).not.toHaveBeenCalled();

    reconhecimento.onstart?.();

    tick(9_999);
    expect(reconhecimento.stop).not.toHaveBeenCalled();

    tick(1);
    expect(reconhecimento.stop).toHaveBeenCalledTimes(1);
    expect(service.estado()).toBe('processando');

    reconhecimento.encerrar();
    flushMicrotasks();

    expect(resposta).toBeNull();
    expect(service.erro()).toBe('sem-fala');
  }));

  it('encerra a promessa se o navegador não emitir end', fakeAsync(() => {
    const service = criarServico();

    let resposta: ResultadoVoz | null | undefined;

    void service.ouvir().then((resultado) => {
      resposta = resultado;
    });

    const reconhecimento = reconhecimentoAtual();

    reconhecimento.onstart?.();

    tick(10_000);
    expect(reconhecimento.stop).toHaveBeenCalledTimes(1);

    tick(5_000);
    flushMicrotasks();

    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
    expect(resposta).toBeNull();
  }));

  it('remove o temporizador ao cancelar', fakeAsync(() => {
    const service = criarServico();

    void service.ouvir();

    const reconhecimento = reconhecimentoAtual();

    reconhecimento.onstart?.();
    service.cancelar();
    tick(40_000);
    flushMicrotasks();

    expect(reconhecimento.stop).not.toHaveBeenCalled();
    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
  }));

  it('funciona com webkitSpeechRecognition', async () => {
    const service = criarServico({
      isSecureContext: true,
      webkitSpeechRecognition: ReconhecimentoFalso,
    });

    expect(service.suportado).toBeTrue();

    const promessa = service.ouvir();

    expect(reconhecimentoAtual().start).toHaveBeenCalled();

    service.cancelar();
    expect(await promessa).toBeNull();
  });

  it('não inicia reconhecimento em navegador sem suporte', async () => {
    const service = criarServico({
      isSecureContext: true,
    });

    expect(service.suportado).toBeFalse();
    expect(await service.ouvir()).toBeNull();
    expect(service.estado()).toBe('inativo');
    expect(ReconhecimentoFalso.instancias.length).toBe(0);
  });

  it('não inicia reconhecimento em contexto inseguro', async () => {
    const service = criarServico({
      isSecureContext: false,
      SpeechRecognition: ReconhecimentoFalso,
    });

    expect(service.suportado).toBeFalse();
    expect(await service.ouvir()).toBeNull();
    expect(ReconhecimentoFalso.instancias.length).toBe(0);
  });

  it('não inicia reconhecimento sem ambiente de navegador', async () => {
    const service = criarServico(null);

    expect(service.suportado).toBeFalse();
    expect(await service.ouvir()).toBeNull();
  });

  it('cancela a escuta quando o contexto do chamador é destruído', async () => {
    const service = criarServico();

    const contexto = createEnvironmentInjector([], TestBed.inject(EnvironmentInjector));

    const promessa = service.ouvir(contexto.get(DestroyRef));
    const reconhecimento = reconhecimentoAtual();

    contexto.destroy();

    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
    expect(await promessa).toBeNull();
    expect(service.estado()).toBe('inativo');
  });

  it('limpa o erro ao iniciar uma nova escuta', async () => {
    const service = criarServico();

    const primeira = service.ouvir();
    reconhecimentoAtual().emitirErro('network');

    expect(await primeira).toBeNull();
    expect(service.erro()).toBe('sem-rede');

    const segunda = service.ouvir();

    expect(service.erro()).toBeNull();
    expect(service.estado()).toBe('ouvindo');

    service.cancelar();
    expect(await segunda).toBeNull();
  });

  it('mantém apenas resultados interinos no texto parcial', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    reconhecimento.onstart?.();

    const final: ResultadoReconhecimento = Object.assign(
      [{ transcript: 'Losartana', confidence: 0.95 }],
      { isFinal: true },
    );

    const interino: ResultadoReconhecimento = Object.assign(
      [{ transcript: 'potássica', confidence: 0.8 }],
      { isFinal: false },
    );

    reconhecimento.onresult?.({
      resultIndex: 0,
      results: [final, interino],
    });

    expect(service.parcial()).toBe('potássica');

    const segundoFinal: ResultadoReconhecimento = Object.assign(
      [{ transcript: 'potássica', confidence: 0.95 }],
      { isFinal: true },
    );

    reconhecimento.onresult?.({
      resultIndex: 1,
      results: [final, segundoFinal],
    });

    expect(service.parcial()).toBe('');

    reconhecimento.encerrar();

    expect(await promessa).toEqual({
      texto: 'Losartana potássica',
      alternativas: [],
    });
  });

  it('encerra a inicialização quando o navegador não emite start', fakeAsync(() => {
    const service = criarServico();

    let resposta: ResultadoVoz | null | undefined;

    void service.ouvir().then((resultado) => {
      resposta = resultado;
    });

    const reconhecimento = reconhecimentoAtual();

    tick(29_999);
    expect(reconhecimento.abort).not.toHaveBeenCalled();

    tick(1);
    flushMicrotasks();

    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
    expect(reconhecimento.stop).not.toHaveBeenCalled();
    expect(resposta).toBeNull();
    expect(service.erro()).toBe('outro');
    expect(service.estado()).toBe('erro');
  }));

  it('trata uma exceção síncrona no start', async () => {
    class ReconhecimentoComFalha extends ReconhecimentoFalso {
      constructor() {
        super();

        this.start.and.callFake(() => {
          throw new DOMException('Permissão negada', 'NotAllowedError');
        });
      }
    }

    const service = criarServico({
      isSecureContext: true,
      SpeechRecognition: ReconhecimentoComFalha,
    });

    expect(await service.ouvir()).toBeNull();
    expect(service.erro()).toBe('sem-permissao');
    expect(service.estado()).toBe('erro');
    expect(reconhecimentoAtual().abort).toHaveBeenCalledTimes(1);
  });

  it('trata uma exceção síncrona no stop', async () => {
    const service = criarServico();
    const promessa = service.ouvir();
    const reconhecimento = reconhecimentoAtual();

    reconhecimento.onstart?.();

    reconhecimento.stop.and.callFake(() => {
      throw new DOMException('Estado inválido', 'InvalidStateError');
    });

    service.parar();

    expect(await promessa).toBeNull();
    expect(service.erro()).toBe('outro');
    expect(service.estado()).toBe('erro');
    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
  });

  it('não prolonga a finalização com eventos de resultado repetidos', fakeAsync(() => {
    const service = criarServico();

    let resposta: ResultadoVoz | null | undefined;

    void service.ouvir().then((resultado) => {
      resposta = resultado;
    });

    const reconhecimento = reconhecimentoAtual();

    reconhecimento.onstart?.();
    reconhecimento.emitirResultado(true, 'Losartana');

    tick(4_000);

    reconhecimento.emitirResultado(true, 'Losartana');

    tick(1_000);
    flushMicrotasks();

    expect(resposta).toEqual({
      texto: 'Losartana',
      alternativas: [],
    });

    expect(reconhecimento.abort).toHaveBeenCalledTimes(1);
    expect(service.estado()).toBe('inativo');
    expect(service.erro()).toBeNull();
  }));
});
