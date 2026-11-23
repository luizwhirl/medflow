/**
 * Situação da última sincronização da UBS com a BNAFAR / e-SUS AF:
 * em dia até 1 h, atrasada até 24 h e crítica depois disso (ou se nunca sincronizou).
 */
export type NivelSincronizacao = 'em-dia' | 'atrasada' | 'critica';

const UMA_HORA = 60;
const UM_DIA = 24 * UMA_HORA;

/** Minutos desde `em` (ISO-8601), ou `null` se a unidade nunca sincronizou. */
export function minutosDesde(em: string | null, agora = Date.now()): number | null {
  return em ? Math.max(0, (agora - new Date(em).getTime()) / 60_000) : null;
}

export function nivelSincronizacao(minutos: number | null): NivelSincronizacao {
  if (minutos === null || minutos > UM_DIA) return 'critica';
  return minutos > UMA_HORA ? 'atrasada' : 'em-dia';
}

/** "há 5 min", "há 3 h", "há 2 dias". */
export function tempoDesde(minutos: number): string {
  if (minutos < UMA_HORA) return `há ${Math.round(minutos)} min`;
  if (minutos < UM_DIA) return `há ${Math.round(minutos / UMA_HORA)} h`;
  const dias = Math.round(minutos / UM_DIA);
  return `há ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
}
