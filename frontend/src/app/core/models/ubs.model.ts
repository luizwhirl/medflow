import { StatusEstoque } from './status-estoque';

export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO';

/** Turno de atendimento (`HH:mm`); `fecha` antes de `abre` termina no dia seguinte. */
export interface TurnoUbs {
  diaSemana: DiaSemana;
  abre: string;
  fecha: string;
}

export interface Ubs {
  id: number;
  cnes: string;
  nome: string;
  tipo: 'UBS' | 'USF';
  bairro: string;
  endereco: string;
  telefone: string | null;
  horario: string | null;
  latitude: number;
  longitude: number;
  /** Momento da última sincronização com a BNAFAR / e-SUS AF (ISO-8601). */
  ultimaSincronizacao: string | null;
  /** Turnos por dia da semana; vazio quando a UBS não tem horário cadastrado. */
  horarios?: TurnoUbs[];
  /** Se está aberta agora (fuso de Maceió); `null` quando não há horário cadastrado. */
  abertaAgora?: boolean | null;
  /** Hora em que fecha (`HH:mm`), só quando está aberta e tem hora de fechar. */
  fechaAs?: string | null;
  /** Data e hora em que fecha, com fuso (ISO-8601); pode ser no dia seguinte. */
  fechaEm?: string | null;
}

/**
 * UBS da busca por proximidade (`GET /api/ubs?lat&lng`), já ordenada pela distância.
 * `status` e `quantidade` só vêm com `medicamentoId`, e só nas unidades que têm o item.
 */
export interface UbsProxima extends Ubs {
  /** Distância em km calculada pela API (duas casas decimais). */
  distanciaKm: number;
  status?: StatusEstoque;
  quantidade?: number;
}
