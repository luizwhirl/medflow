import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';

import type {
  AmbienteReconhecimentoVoz,
  EventoResultadoReconhecimento,
  ReconhecimentoVoz,
} from '../types/speech-recognition';

export type EstadoVoz = 'inativo' | 'ouvindo' | 'processando' | 'erro';

export type ErroVoz =
  'sem-permissao' | 'sem-fala' | 'sem-microfone' | 'sem-rede' | 'idioma-nao-suportado' | 'outro';

export interface ResultadoVoz {
  texto: string;
  alternativas: string[];
}

export const AMBIENTE_RECONHECIMENTO_VOZ = new InjectionToken<AmbienteReconhecimentoVoz | null>(
  'Ambiente de reconhecimento de voz',
  {
    providedIn: 'root',
    factory: () => inject(DOCUMENT).defaultView as AmbienteReconhecimentoVoz | null,
  },
);

interface Escuta {
  reconhecimento: ReconhecimentoVoz;
  resolver: (resultado: ResultadoVoz | null) => void;
  resultado: ResultadoVoz | null;
  temporizador?: ReturnType<typeof setTimeout>;
  removerDestroy?: () => void;
  parando: boolean;
  iniciada: boolean;
  aguardandoFim: boolean;
}

const LIMITE_INICIALIZACAO_MS = 30_000;
const LIMITE_ESCUTA_MS = 10_000;
const LIMITE_FINALIZACAO_MS = 5_000;

@Injectable({ providedIn: 'root' })
export class ReconhecimentoVozService {
  private readonly ambiente = inject(AMBIENTE_RECONHECIMENTO_VOZ);
  private readonly destroyRef = inject(DestroyRef);

  private readonly construtor = this.ambiente?.isSecureContext
    ? (this.ambiente.SpeechRecognition ?? this.ambiente.webkitSpeechRecognition)
    : undefined;

  readonly suportado = this.construtor !== undefined;

  private readonly estadoInterno = signal<EstadoVoz>('inativo');
  private readonly parcialInterno = signal('');
  private readonly erroInterno = signal<ErroVoz | null>(null);

  readonly estado = this.estadoInterno.asReadonly();
  readonly parcial = this.parcialInterno.asReadonly();
  readonly erro = this.erroInterno.asReadonly();

  private escuta: Escuta | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.cancelar());
  }

  /**
   * Inicia uma escuta. Se houver outra em andamento, cancela a anterior.
   * Componentes devem passar seu próprio DestroyRef para cancelar
   * a escuta automaticamente ao navegar ou destruir a tela.
   * O serviço é compartilhado e possui ciclo de vida da aplicação.
   */
  ouvir(dono?: DestroyRef): Promise<ResultadoVoz | null> {
    this.cancelar();

    this.erroInterno.set(null);
    this.parcialInterno.set('');
    this.estadoInterno.set('inativo');

    const Construtor = this.construtor;

    if (!Construtor || this.destroyRef.destroyed || dono?.destroyed) {
      return Promise.resolve(null);
    }

    let reconhecimento: ReconhecimentoVoz;

    try {
      reconhecimento = new Construtor();

      reconhecimento.lang = 'pt-BR';
      reconhecimento.continuous = false;
      reconhecimento.interimResults = true;
      reconhecimento.maxAlternatives = 3;
    } catch (erro) {
      this.erroInterno.set(this.mapearExcecao(erro));
      this.estadoInterno.set('erro');
      return Promise.resolve(null);
    }

    return new Promise<ResultadoVoz | null>((resolver) => {
      const escuta: Escuta = {
        reconhecimento,
        resolver,
        resultado: null,
        parando: false,
        iniciada: false,
        aguardandoFim: false,
      };

      this.escuta = escuta;

      reconhecimento.onstart = () => {
        if (this.escuta !== escuta || escuta.parando || escuta.iniciada || escuta.aguardandoFim) {
          return;
        }

        escuta.iniciada = true;
        this.estadoInterno.set('ouvindo');

        clearTimeout(escuta.temporizador);

        escuta.temporizador = setTimeout(() => {
          if (this.escuta === escuta) {
            this.parar();
          }
        }, LIMITE_ESCUTA_MS);
      };

      reconhecimento.onspeechend = () => {
        if (this.escuta === escuta) {
          this.estadoInterno.set('processando');
        }
      };

      reconhecimento.onresult = (evento) => this.processarResultado(escuta, evento);

      reconhecimento.onnomatch = () => this.finalizar(escuta, null, 'sem-fala', true);

      reconhecimento.onerror = (evento) => {
        if (evento.error === 'aborted') {
          this.finalizar(escuta, null);
          return;
        }

        this.finalizar(escuta, null, this.mapearErro(evento.error));
      };

      reconhecimento.onend = () =>
        this.finalizar(escuta, escuta.resultado, escuta.resultado ? null : 'sem-fala');

      if (dono) {
        escuta.removerDestroy = dono.onDestroy(() => {
          this.finalizar(escuta, null, null, true);
        });
      }

      this.estadoInterno.set('ouvindo');

      // A autorização do microfone não consome o tempo de escuta.
      escuta.temporizador = setTimeout(() => {
        this.finalizar(escuta, null, 'outro', true);
      }, LIMITE_INICIALIZACAO_MS);

      try {
        reconhecimento.start();
      } catch (erro) {
        this.finalizar(escuta, null, this.mapearExcecao(erro), true);
      }
    });
  }

  /** Para de captar áudio, permitindo receber o resultado final. */
  parar(): void {
    const escuta = this.escuta;

    if (!escuta || escuta.parando) {
      return;
    }

    escuta.parando = true;
    this.estadoInterno.set('processando');
    this.aguardarFim(escuta);

    try {
      escuta.reconhecimento.stop();
    } catch (erro) {
      this.finalizar(escuta, null, this.mapearExcecao(erro), true);
    }
  }

  /** Cancela a escuta e descarta qualquer resultado. */
  cancelar(): void {
    if (this.escuta) {
      this.finalizar(this.escuta, null, null, true);
    }
  }

  private processarResultado(escuta: Escuta, evento: EventoResultadoReconhecimento): void {
    if (this.escuta !== escuta) {
      return;
    }

    // A API pode devolver resultados anteriores e parciais atualizados.
    const resultados = Array.from(evento.results);

    const finais = resultados.filter((resultado) => resultado.isFinal && resultado.length > 0);

    const textos = finais.map((resultado) => resultado[0].transcript.trim());

    const texto = textos.join(' ').trim();

    if (texto) {
      const candidatos = finais.flatMap((resultado, indice) =>
        Array.from(resultado)
          .slice(1)
          .map((alternativa) => ({
            texto: textos
              .map((trecho, i) => (i === indice ? alternativa.transcript.trim() : trecho))
              .join(' ')
              .trim(),
            confianca: alternativa.confidence,
          })),
      );

      candidatos.sort((a, b) => b.confianca - a.confianca);

      const alternativas = [
        ...new Set(
          candidatos
            .map((candidato) => candidato.texto)
            .filter((alternativa) => alternativa && alternativa !== texto),
        ),
      ].slice(0, 2);

      escuta.resultado = { texto, alternativas };
    }

    const temInterino = resultados.some((resultado) => !resultado.isFinal);

    this.parcialInterno.set(
      temInterino
        ? resultados
            .filter((resultado) => !resultado.isFinal)
            .map((resultado) => resultado[0]?.transcript.trim() ?? '')
            .join(' ')
            .trim()
        : '',
    );

    if (escuta.resultado && !temInterino) {
      this.estadoInterno.set('processando');
      this.aguardarFim(escuta);
    }
  }

  /**
   * Aguarda o evento end, com proteção para navegadores que
   * eventualmente não finalizem a sessão corretamente.
   */
  private aguardarFim(escuta: Escuta): void {
    if (escuta.aguardandoFim) {
      return;
    }

    escuta.aguardandoFim = true;
    clearTimeout(escuta.temporizador);

    escuta.temporizador = setTimeout(() => {
      this.finalizar(escuta, escuta.resultado, escuta.resultado ? null : 'sem-fala', true);
    }, LIMITE_FINALIZACAO_MS);
  }

  private finalizar(
    escuta: Escuta,
    resultado: ResultadoVoz | null,
    erro: ErroVoz | null = null,
    abortar = false,
  ): void {
    // Eventos atrasados de uma escuta cancelada não afetam a atual.
    if (this.escuta !== escuta) {
      return;
    }

    this.escuta = null;

    clearTimeout(escuta.temporizador);
    escuta.removerDestroy?.();

    const reconhecimento = escuta.reconhecimento;

    reconhecimento.onstart = null;
    reconhecimento.onspeechend = null;
    reconhecimento.onresult = null;
    reconhecimento.onnomatch = null;
    reconhecimento.onerror = null;
    reconhecimento.onend = null;

    if (abortar) {
      try {
        reconhecimento.abort();
      } catch {
        // A sessão já pode ter sido encerrada pelo navegador.
      }
    }

    this.parcialInterno.set('');
    this.erroInterno.set(erro);
    this.estadoInterno.set(erro ? 'erro' : 'inativo');

    escuta.resolver(resultado);
  }

  private mapearErro(codigo: string): ErroVoz {
    switch (codigo) {
      case 'not-allowed':
      case 'service-not-allowed':
        return 'sem-permissao';
      case 'no-speech':
        return 'sem-fala';
      case 'audio-capture':
        return 'sem-microfone';
      case 'network':
        return 'sem-rede';
      case 'language-not-supported':
        return 'idioma-nao-suportado';
      default:
        return 'outro';
    }
  }

  private mapearExcecao(erro: unknown): ErroVoz {
    if (erro instanceof DOMException) {
      switch (erro.name) {
        case 'NotAllowedError':
        case 'SecurityError':
          return 'sem-permissao';
        case 'NotFoundError':
        case 'NotReadableError':
          return 'sem-microfone';
        case 'NetworkError':
          return 'sem-rede';
      }
    }

    return 'outro';
  }
}
