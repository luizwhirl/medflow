import { Ubs } from './models/ubs.model';

/** Com até esta antecedência do fechamento, o texto vira "Fecha às ..." (tom de alerta). */
export const MINUTOS_FECHANDO = 60;

export type TomFuncionamento = 'aberta' | 'fechando' | 'fechada';

export interface SituacaoFuncionamento {
  /** Ex.: "Aberta até 17h", "Fecha às 16h", "Aberta 24 horas", "Fechada agora". */
  texto: string;
  tom: TomFuncionamento;
}

type DadosFuncionamento = Pick<Ubs, 'abertaAgora' | 'fechaAs' | 'fechaEm'>;

/** "17:00" vira "17h"; "17:30" vira "17h30". */
export function horaCurta(hhmm: string): string {
  const [h, m] = hhmm.split(':');
  return `${Number(h)}h${m && m !== '00' ? m : ''}`;
}

/**
 * Texto de aberta/fechada dos cartões de UBS, a partir de `abertaAgora`/`fechaAs`/`fechaEm` da API.
 * `null` quando a UBS não tem horário cadastrado (a situação é desconhecida, então não se mostra nada).
 */
export function situacaoFuncionamento(
  ubs: DadosFuncionamento,
  agora = Date.now(),
): SituacaoFuncionamento | null {
  if (ubs.abertaAgora == null) return null;
  if (!ubs.abertaAgora) return { texto: 'Fechada agora', tom: 'fechada' };
  if (!ubs.fechaAs) return { texto: 'Aberta 24 horas', tom: 'aberta' };

  const fecha = horaCurta(ubs.fechaAs);
  const minutos = ubs.fechaEm ? (new Date(ubs.fechaEm).getTime() - agora) / 60_000 : Infinity;
  return minutos <= MINUTOS_FECHANDO
    ? { texto: `Fecha às ${fecha}`, tom: 'fechando' }
    : { texto: `Aberta até ${fecha}`, tom: 'aberta' };
}
