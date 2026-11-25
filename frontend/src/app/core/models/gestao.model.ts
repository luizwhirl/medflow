/** Situação de um item no painel da gestão (ordem = prioridade na tabela). */
export type SituacaoItem = 'EM_FALTA' | 'ABAIXO_MINIMO' | 'PEDIDO_ENVIADO';

export interface UbsResumo {
  id: number;
  nome: string;
}

export interface UbsEscopo extends UbsResumo {
  cnes: string;
}

/** `GET /api/gestao/indicadores`. */
export interface IndicadoresResponse {
  /** `null` quando o escopo é a rede toda (gestor sem vínculo). */
  ubs: UbsEscopo | null;
  restrito: boolean;
  ultimaSincronizacao: string | null;
  itensEmFalta: number;
  emFaltaComRemessa: number;
  abaixoDoMinimo: number;
  remessasACaminho: number;
  remessasAtrasadas: number;
  proximaRemessa: string | null;
  pacientesAguardando: number;
  medicamentosAguardados: number;
  lotesVencendo: number;
  ubsComSincronizacaoAtrasada: number;
}

/** Item de `GET /api/gestao/itens-atencao`. */
export interface ItemAtencaoResponse {
  ubs: UbsResumo;
  medicamento: UbsResumo;
  estoque: number;
  minimo: number;
  aguardandoAviso: number;
  situacao: SituacaoItem;
  remessaPrevista: string | null;
}

/** `GET /api/gestao/demanda-reprimida` (só o que o painel usa). */
export interface DemandaReprimidaResponse {
  maisProcurados: { medicamento: UbsResumo; buscas: number }[];
}

export interface ItemAtencao {
  ubsId: number;
  /** Nome da UBS: aparece na tabela quando o painel mostra a rede toda. */
  ubs: string;
  medicamentoId: number;
  medicamento: string;
  estoque: number;
  minimo: number;
  aguardandoAviso: number;
  situacao: SituacaoItem;
  /** Data prevista da próxima remessa (ISO `yyyy-MM-dd`), se houver. */
  remessaPrevista: string | null;
}

export interface MaisProcurado {
  medicamentoId: number;
  medicamento: string;
  buscas: number;
}

/** O painel montado a partir dos três endpoints da gestão. */
export interface PainelGestao {
  ubs: UbsEscopo | null;
  restrito: boolean;
  ultimaSincronizacao: string | null;
  indicadores: Omit<IndicadoresResponse, 'ubs' | 'restrito' | 'ultimaSincronizacao'>;
  itensAtencao: ItemAtencao[];
  maisProcurados: MaisProcurado[];
}

/** De onde vem o pino da UBS no mapa (`GET /api/gestao/ubs/{id}/localizacao`). */
export type OrigemLocalizacao = 'FONTE' | 'LUGAR' | 'BAIRRO' | 'MANUAL' | 'INVALIDA';

export interface LocalizacaoUbs {
  ubsId: number;
  nome: string;
  endereco: string;
  bairro: string;
  latitude: number;
  longitude: number;
  origem: OrigemLocalizacao;
  aproximada: boolean;
}
