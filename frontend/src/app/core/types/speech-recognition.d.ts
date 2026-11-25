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

export interface ConstrutorReconhecimentoVoz {
  new (): ReconhecimentoVoz;
}

export interface AmbienteReconhecimentoVoz {
  readonly isSecureContext: boolean;
  readonly SpeechRecognition?: ConstrutorReconhecimentoVoz;
  readonly webkitSpeechRecognition?: ConstrutorReconhecimentoVoz;
}
