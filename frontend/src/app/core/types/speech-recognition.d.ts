export interface AlternativaReconhecimento {
  readonly transcript: string;
  readonly confidence: number;
}

export interface ResultadoReconhecimento extends ArrayLike<AlternativaReconhecimento> {
  readonly isFinal: boolean;
}

export interface EventoResultadoReconhecimento {
  readonly resultIndex: number;
  readonly results: ArrayLike<ResultadoReconhecimento>;
}

export interface EventoErroReconhecimento {
  readonly error: string;
}

export interface ReconhecimentoVoz {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  /** Reconhecimento no próprio aparelho, sem mandar o áudio para fora (Chrome 139+). */
  processLocally?: boolean;

  onstart: (() => void) | null;
  onspeechend: (() => void) | null;
  onresult: ((evento: EventoResultadoReconhecimento) => void) | null;
  onnomatch: (() => void) | null;
  onerror: ((evento: EventoErroReconhecimento) => void) | null;
  onend: (() => void) | null;

  start(): void;
  stop(): void;
  abort(): void;
}

export type DisponibilidadeReconhecimento =
  'available' | 'downloadable' | 'downloading' | 'unavailable';

export interface ConstrutorReconhecimentoVoz {
  new (): ReconhecimentoVoz;
  /** Diz se o idioma pode ser reconhecido no aparelho; só existe nos navegadores mais novos. */
  available?(opcoes: {
    langs: string[];
    processLocally: boolean;
  }): Promise<DisponibilidadeReconhecimento>;
}

export interface AmbienteReconhecimentoVoz {
  readonly isSecureContext: boolean;
  readonly SpeechRecognition?: ConstrutorReconhecimentoVoz;
  readonly webkitSpeechRecognition?: ConstrutorReconhecimentoVoz;
}
