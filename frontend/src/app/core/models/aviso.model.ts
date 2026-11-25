export type TipoAviso = 'MEDICAMENTO_DISPONIVEL' | 'UBS_DADOS_ATRASADOS';

/** Aviso do aparelho (`GET /api/avisos`, cabeçalho `X-Dispositivo-Id`); o texto vem pronto da API. */
export interface Aviso {
  id: number;
  tipo: TipoAviso;
  titulo: string;
  texto: string;
  lido: boolean;
  criadoEm: string;
}

export type TomAviso = 'disponivel' | 'alerta';

export function tomDoAviso(a: Pick<Aviso, 'tipo'>): TomAviso {
  return a.tipo === 'UBS_DADOS_ATRASADOS' ? 'alerta' : 'disponivel';
}

/** "agora", "há 5 min", "há 3 h", "ontem", "há 4 dias" ou a data, para datas mais antigas. */
export function quandoAconteceu(iso: string | undefined, agora = Date.now()): string {
  if (!iso) return '';
  const min = Math.max(0, (agora - new Date(iso).getTime()) / 60_000);
  if (min < 1) return 'agora';
  if (min < 60) return `há ${Math.round(min)} min`;
  if (min < 24 * 60) return `há ${Math.round(min / 60)} h`;
  const dias = Math.floor(min / (24 * 60));
  if (dias === 1) return 'ontem';
  if (dias < 7) return `há ${dias} dias`;
  return new Date(iso).toLocaleDateString('pt-BR');
}
