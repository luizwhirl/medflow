const CHAVE = 'medflow.buscas-contadas';

/** Dia de hoje em Maceió (`2026-11-25`), o mesmo fuso da contagem na API. */
function hoje(agora = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Maceio',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(agora);
}

/**
 * Diz se a procura deste medicamento ainda não foi contada hoje neste aparelho e, se não foi,
 * já a marca como contada. Evita contar de novo ao recarregar a página, voltar no navegador ou
 * reabrir o mesmo remédio. Guarda só o dia e os ids, nada que identifique a pessoa.
 */
export function primeiraProcuraDoDia(medicamentoId: number, agora = new Date()): boolean {
  const dia = hoje(agora);
  let ids: number[] = [];
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as {
      dia?: string;
      ids?: unknown;
    } | null;
    if (salvo?.dia === dia && Array.isArray(salvo.ids)) {
      ids = salvo.ids.filter((x): x is number => typeof x === 'number');
    }
  } catch {
    // valor corrompido: recomeça o dia
  }
  if (ids.includes(medicamentoId)) return false;
  try {
    localStorage.setItem(CHAVE, JSON.stringify({ dia, ids: [...ids, medicamentoId] }));
  } catch {
    // sem armazenamento (aba privada): conta, mas não consegue lembrar
  }
  return true;
}
