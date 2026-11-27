/**
 * Situação dos dados da UBS na BNAFAR / e-SUS AF.
 *
 * A BNAFAR publica uma posição de estoque por dia, e a data dela vira a "última sincronização" (meia-noite do dia
 * da posição). Por isso a conta é em dias, como no painel da gestão: a posição de ontem vale o dia todo de hoje
 * (até 48 h), atrasada até 4 dias e crítica depois disso (ou se nunca sincronizou).
 */
/** Telas abertas (detalhe da UBS, painel da gestão) buscam os dados de novo neste intervalo. */
export const ATUALIZAR_DADOS_A_CADA_MS = 5 * 60_000;

export type NivelSincronizacao = 'em-dia' | 'atrasada' | 'critica';

const UMA_HORA = 60;
const UM_DIA = 24 * UMA_HORA;
const EM_DIA_ATE = 2 * UM_DIA;
const ATRASADA_ATE = 4 * UM_DIA;

/** Minutos desde `em` (ISO-8601), ou `null` se a unidade nunca sincronizou. */
export function minutosDesde(em: string | null, agora = Date.now()): number | null {
  return em ? Math.max(0, (agora - new Date(em).getTime()) / 60_000) : null;
}

export function nivelSincronizacao(minutos: number | null): NivelSincronizacao {
  if (minutos === null || minutos > ATRASADA_ATE) return 'critica';
  return minutos > EM_DIA_ATE ? 'atrasada' : 'em-dia';
}

/** "há 5 min", "há 3 h", "há 2 dias". */
export function tempoDesde(minutos: number): string {
  if (minutos < UMA_HORA) return `há ${Math.round(minutos)} min`;
  if (minutos < UM_DIA) return `há ${Math.round(minutos / UMA_HORA)} h`;
  const dias = Math.round(minutos / UM_DIA);
  return `há ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
}
