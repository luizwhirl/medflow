import { StatusEstoque } from './status-estoque';

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
