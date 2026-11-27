import { EtapaRemessa, RemessaRastreamento } from './models/rastreamento.model';
import { formatarDia, linhaDoTempo, textoChegada } from './remessa';

const ORDEM: EtapaRemessa[] = ['PEDIDO', 'SEPARADO_CAF', 'EM_TRANSPORTE', 'RECEBIDO', 'DISPONIVEL'];

/** Remessa como a API devolve, com as datas informadas (as outras etapas vêm com `null`). */
function remessa(datas: Partial<Record<EtapaRemessa, string>>): RemessaRastreamento {
  const feitas = ORDEM.filter((e) => datas[e]);
  return {
    id: 4,
    quantidade: 300,
    previsaoChegada: '2026-11-26',
    etapaAtual: feitas.at(-1) ?? 'PEDIDO',
    concluida: !!datas.DISPONIVEL,
    linhaDoTempo: ORDEM.map((etapa) => ({ etapa, ocorridoEm: datas[etapa] ?? null })),
  };
}

// 17/11/2026 é uma terça-feira.
const EM_TRANSITO = {
  PEDIDO: '2026-11-17T12:00:00Z',
  SEPARADO_CAF: '2026-11-18T02:00:00Z', // 23h de terça em Maceió
  EM_TRANSPORTE: '2026-11-18T13:00:00Z',
};

describe('formatarDia', () => {
  it('mostra o dia da semana e dd/mm de uma data sem horário', () => {
    expect(formatarDia('2026-10-22')).toBe('qui, 22/10');
    expect(formatarDia('2026-11-22')).toBe('dom, 22/11');
  });

  it('converte um instante para o dia em Maceió, não em UTC', () => {
    expect(formatarDia('2026-11-18T02:00:00Z')).toBe('ter, 17/11');
    expect(formatarDia('2026-11-18T03:00:00Z')).toBe('qua, 18/11');
  });
});

describe('linhaDoTempo', () => {
  it('em trânsito: datas nas etapas feitas e previsão no último passo', () => {
    expect(linhaDoTempo(remessa(EM_TRANSITO))).toEqual({
      etapas: [
        { titulo: 'Pedido aprovado pela UBS', detalhe: 'ter, 17/11' },
        { titulo: 'Separado na Central de Abastecimento', detalhe: 'ter, 17/11' },
        { titulo: 'Em trânsito', detalhe: 'qua, 18/11' },
        { titulo: 'Disponível na sua UBS', detalhe: 'Previsão: qui, 26/11' },
      ],
      atual: 2,
    });
  });

  it('recebida e ainda em conferência: o último passo não conclui', () => {
    const l = linhaDoTempo(remessa({ ...EM_TRANSITO, RECEBIDO: '2026-11-19T15:00:00Z' }));
    expect(l.atual).toBe(2);
    expect(l.etapas[3].detalhe).toBe('Chegou qui, 19/11 · em conferência');
  });

  it('entregue: todos os passos concluídos, com a data de disponível', () => {
    const l = linhaDoTempo(
      remessa({
        ...EM_TRANSITO,
        RECEBIDO: '2026-11-19T15:00:00Z',
        DISPONIVEL: '2026-11-19T18:00:00Z',
      }),
    );
    expect(l.atual).toBe(3);
    expect(l.etapas[3].detalhe).toBe('qui, 19/11');
  });

  it('só com o pedido, conclui o primeiro passo', () => {
    const l = linhaDoTempo(remessa({ PEDIDO: '2026-11-17T12:00:00Z' }));
    expect(l.atual).toBe(0);
    expect(l.etapas.map((e) => e.detalhe)).toEqual([
      'ter, 17/11',
      null,
      null,
      'Previsão: qui, 26/11',
    ]);
  });

  it('sem nenhuma data, nenhum passo concluído', () => {
    expect(linhaDoTempo(remessa({})).atual).toBeNull();
  });
});

describe('textoChegada', () => {
  it('a caminho: "Chega" com a previsão', () => {
    expect(textoChegada(remessa(EM_TRANSITO))).toBe('Chega qui, 26/11');
  });

  it('recebida: em conferência', () => {
    expect(textoChegada(remessa({ ...EM_TRANSITO, RECEBIDO: '2026-11-19T15:00:00Z' }))).toBe(
      'Chegou, em conferência',
    );
  });

  it('entregue ou sem remessa: nada', () => {
    expect(
      textoChegada(remessa({ ...EM_TRANSITO, DISPONIVEL: '2026-11-19T18:00:00Z' })),
    ).toBeNull();
    expect(textoChegada(null)).toBeNull();
  });
});
