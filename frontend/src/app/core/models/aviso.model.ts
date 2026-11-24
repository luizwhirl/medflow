/** Tipos de aviso gerados pela API (`/api/avisos`). */
export type TipoAviso = 'MEDICAMENTO_DISPONIVEL' | 'UBS_DADOS_ATRASADOS';

/** Aviso do aparelho (sem login), do mais recente para o mais antigo. */
export interface Aviso {
  id: number;
  tipo: TipoAviso;
  titulo: string;
  texto: string;
  lido: boolean;
  /** Instante ISO-8601. */
  criadoEm: string;
}
