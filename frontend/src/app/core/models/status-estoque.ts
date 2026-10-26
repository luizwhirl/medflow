export type StatusEstoque = 'DISPONIVEL' | 'RESERVADO' | 'EM_FALTA';

export const STATUS_LABEL: Record<StatusEstoque, string> = {
  DISPONIVEL: 'Disponível',
  RESERVADO: 'Reservado',
  EM_FALTA: 'Em falta',
};
