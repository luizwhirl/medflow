/** Formato único das respostas paginadas da API (`pagina` começa em 0). */
export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

/** Maior `tamanho` aceito pela API. */
export const TAMANHO_MAXIMO_PAGINA = 100;
