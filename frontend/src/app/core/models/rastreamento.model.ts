/** Etapas da remessa da Central de Abastecimento (CAF) até a UBS, na ordem em que acontecem. */
export type EtapaRemessa = 'PEDIDO' | 'SEPARADO_CAF' | 'EM_TRANSPORTE' | 'RECEBIDO' | 'DISPONIVEL';

export interface EtapaRastreamento {
  etapa: EtapaRemessa;
  /** Instante ISO-8601; `null` enquanto a remessa não chegou nesta etapa. */
  ocorridoEm: string | null;
}

export interface RemessaRastreamento {
  id: number;
  quantidade: number;
  /** Data local (`2026-11-22`), sem horário. */
  previsaoChegada: string;
  etapaAtual: EtapaRemessa;
  /** Verdadeiro quando a remessa já está DISPONIVEL na UBS. */
  concluida: boolean;
  /** Sempre as cinco etapas, na ordem. */
  linhaDoTempo: EtapaRastreamento[];
}

/** `GET /api/medicamentos/{id}/rastreamento?ubsId=`; `remessa` é `null` sem remessa para a UBS. */
export interface Rastreamento {
  medicamentoId: number;
  ubsId: number;
  remessa: RemessaRastreamento | null;
}
