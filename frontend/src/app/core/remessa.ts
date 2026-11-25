import { EtapaRemessa, RemessaRastreamento } from './models/rastreamento.model';

/** Fuso das UBS atendidas: as datas da remessa são mostradas no horário de Maceió. */
const FUSO = 'America/Maceio';
const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

/**
 * "qui, 22/10". Aceita uma data (`2026-10-22`, usada como está) ou um instante ISO-8601
 * (`2026-10-22T02:00:00Z`, convertido para o dia em Maceió).
 */
export function formatarDia(valor: string): string {
  const data = /^\d{4}-\d{2}-\d{2}$/.test(valor)
    ? valor
    : new Intl.DateTimeFormat('en-CA', {
        timeZone: FUSO,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(new Date(valor));
  const [ano, mes, dia] = data.split('-').map(Number);
  const semana = new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay();
  return `${DIAS[semana]}, ${String(dia).padStart(2, '0')}/${String(mes).padStart(2, '0')}`;
}

/** Etapa da tela (Figma: quatro passos) e as etapas da API que ela resume. */
interface PassoRemessa {
  titulo: string;
  etapas: readonly EtapaRemessa[];
}

/**
 * A API tem cinco etapas; a tela mostra quatro. RECEBIDO (chegou na UBS, ainda em conferência)
 * fica dentro do último passo, que só conclui com DISPONIVEL.
 */
export const PASSOS_REMESSA: readonly PassoRemessa[] = [
  { titulo: 'Pedido aprovado pela UBS', etapas: ['PEDIDO'] },
  { titulo: 'Separado na Central de Abastecimento', etapas: ['SEPARADO_CAF'] },
  { titulo: 'Em trânsito', etapas: ['EM_TRANSPORTE'] },
  { titulo: 'Disponível na sua UBS', etapas: ['RECEBIDO', 'DISPONIVEL'] },
];

export interface LinhaDoTempoRemessa {
  etapas: { titulo: string; detalhe: string | null }[];
  /** Índice do último passo concluído; `null` se nenhum. */
  atual: number | null;
}

/** Passos da tela com a data de cada um e a previsão no passo que falta. */
export function linhaDoTempo(remessa: RemessaRastreamento): LinhaDoTempoRemessa {
  const quando = new Map(remessa.linhaDoTempo.map((e) => [e.etapa, e.ocorridoEm]));
  const dataDe = (etapa: EtapaRemessa) => quando.get(etapa) ?? null;

  const etapas = PASSOS_REMESSA.map((passo, i) => {
    const ultimo = i === PASSOS_REMESSA.length - 1;
    if (!ultimo) {
      const em = dataDe(passo.etapas[0]);
      return { titulo: passo.titulo, detalhe: em ? formatarDia(em) : null };
    }
    const disponivel = dataDe('DISPONIVEL');
    const recebido = dataDe('RECEBIDO');
    const detalhe = disponivel
      ? formatarDia(disponivel)
      : recebido
        ? `Chegou ${formatarDia(recebido)} · em conferência`
        : `Previsão: ${formatarDia(remessa.previsaoChegada)}`;
    return { titulo: passo.titulo, detalhe };
  });

  // o último passo só conta como concluído com DISPONIVEL; os outros, com a própria etapa
  const concluidos = PASSOS_REMESSA.map((passo, i) =>
    i === PASSOS_REMESSA.length - 1
      ? dataDe('DISPONIVEL') !== null
      : dataDe(passo.etapas[0]) !== null,
  );
  const atual = concluidos.lastIndexOf(true);
  return { etapas, atual: atual === -1 ? null : atual };
}

/**
 * "Chega qui, 22/10" para remessa a caminho, "Chegou, em conferência" quando já foi recebida
 * mas ainda não está disponível; `null` sem remessa ou se já está disponível.
 */
export function textoChegada(remessa: RemessaRastreamento | null | undefined): string | null {
  if (!remessa || remessa.concluida) return null;
  const recebida = remessa.linhaDoTempo.some((e) => e.etapa === 'RECEBIDO' && e.ocorridoEm);
  return recebida ? 'Chegou, em conferência' : `Chega ${formatarDia(remessa.previsaoChegada)}`;
}
