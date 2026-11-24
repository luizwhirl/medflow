export type TipoAviso = 'DISPONIVEL' | 'REMESSA' | 'SINCRONIZACAO_ATRASADA';
export type EtapaRemessa = 'PEDIDO' | 'SEPARADO_CAF' | 'EM_TRANSPORTE' | 'RECEBIDO' | 'DISPONIVEL';

/** Aviso da API (`GET /api/avisos`); o texto é montado no front (`textoDoAviso`). */
export interface Aviso {
  /** Estável para o mesmo acontecimento: é por ele que guardamos o que já foi lido. */
  id: string;
  tipo: TipoAviso;
  ubsId: number;
  ubsNome: string;
  medicamentoId?: number;
  medicamentoNome?: string;
  medicamentoConcentracao?: string;
  quantidade?: number;
  etapa?: EtapaRemessa;
  /** Data (AAAA-MM-DD), sem hora. */
  previsaoChegada?: string;
  ocorridoEm?: string;
}

export type TomAviso = 'disponivel' | 'info' | 'alerta';

export interface TextoAviso {
  titulo: string;
  detalhe: string;
  tom: TomAviso;
  link: unknown[];
}

const ETAPA: Record<EtapaRemessa, (med: string) => string> = {
  PEDIDO: (m) => `Pedido de ${m} feito`,
  SEPARADO_CAF: (m) => `${m} separado na central`,
  EM_TRANSPORTE: (m) => `${m} a caminho`,
  RECEBIDO: (m) => `${m} recebido na UBS`,
  DISPONIVEL: (m) => `${m} disponível`,
};

/** "qui, 25/11" a partir de "2026-11-25" (sem passar por UTC, para não mudar o dia). */
export function formatarPrevisao(data: string): string {
  const [a, m, d] = data.split('-').map(Number);
  const dia = new Date(a, m - 1, d);
  const semana = dia.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
  return `${semana}, ${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}`;
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

/** "há 26 h" até dois dias, depois "há 3 dias", "há 12 dias": duração do atraso, sem virar data. */
function haQuantoTempo(iso: string, agora = Date.now()): string {
  const horas = Math.max(0, (agora - new Date(iso).getTime()) / 3_600_000);
  if (horas < 48) return `há ${Math.round(horas)} h`;
  const dias = Math.floor(horas / 24);
  return `há ${dias} dias`;
}

export function textoDoAviso(a: Aviso): TextoAviso {
  const med = [a.medicamentoNome, a.medicamentoConcentracao].filter(Boolean).join(' ');
  switch (a.tipo) {
    case 'DISPONIVEL':
      return {
        titulo: `${med} disponível`,
        detalhe: `~${a.quantidade} un. na ${a.ubsNome}. Retire no horário de funcionamento.`,
        tom: 'disponivel',
        link: ['/medicamentos', a.medicamentoId],
      };
    case 'REMESSA':
      return {
        titulo: ETAPA[a.etapa ?? 'PEDIDO'](med),
        detalhe:
          `Remessa de ${a.quantidade} un. para a ${a.ubsNome}.` +
          (a.previsaoChegada ? ` Previsão: ${formatarPrevisao(a.previsaoChegada)}.` : ''),
        tom: 'info',
        link: ['/medicamentos', a.medicamentoId],
      };
    case 'SINCRONIZACAO_ATRASADA':
      return {
        titulo: 'Dados atrasados na sua UBS',
        detalhe: a.ocorridoEm
          ? `A ${a.ubsNome} está sem sincronizar ${haQuantoTempo(a.ocorridoEm)}. Ligue antes de ir.`
          : `A ${a.ubsNome} ainda não sincronizou o estoque. Ligue antes de ir.`,
        tom: 'alerta',
        link: ['/ubs', a.ubsId],
      };
  }
}
