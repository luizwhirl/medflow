/** Situação de um item no painel da gestão (ordem = prioridade na tabela). */
export type SituacaoItem = 'EM_FALTA' | 'ABAIXO_MINIMO' | 'PEDIDO_ENVIADO';

export interface ItemAtencao {
  medicamentoId: number;
  /** Nome com concentração, ex.: "Metformina 850 mg". */
  medicamento: string;
  estoque: number;
  minimo: number;
  aguardandoAviso: number;
  situacao: SituacaoItem;
  /** Data prevista da próxima remessa (ISO `yyyy-MM-dd`), se houver. */
  remessaPrevista: string | null;
}

export interface MaisProcurado {
  medicamento: string;
  buscas: number;
}

/** Resposta de `GET /api/gestao/painel` (PDS-21). */
export interface PainelGestao {
  ubs: { id: number; nome: string; cnes: string };
  ultimaSincronizacao: string | null;
  indicadores: {
    itensEmFalta: number;
    emFaltaComRemessa: number;
    abaixoDoMinimo: number;
    /** Prazo sugerido para repor os itens abaixo do mínimo. */
    prazoReposicaoDias: number;
    remessasACaminho: number;
    proximaRemessa: string | null;
    pacientesAguardando: number;
    medicamentosAguardados: number;
  };
  itensAtencao: ItemAtencao[];
  maisProcurados: MaisProcurado[];
  /** `true` enquanto os números vêm de demonstração e não da API. */
  ficticio: boolean;
}
