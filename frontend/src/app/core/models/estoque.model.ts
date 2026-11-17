import { Medicamento } from './medicamento.model';
import { StatusEstoque } from './status-estoque';
import { Ubs } from './ubs.model';

export interface ItemEstoque {
  medicamento: Medicamento;
  status: StatusEstoque;
  quantidade: number;
  atualizadoEm: string;
}

export interface Disponibilidade {
  ubs: Ubs;
  status: StatusEstoque;
  quantidade: number;
  atualizadoEm: string;
}

/** Regra do backend (docs/api.md): só conta como disponível com status DISPONIVEL e quantidade > 0. */
export function temEstoque(d: { status: StatusEstoque; quantidade: number }): boolean {
  return d.status === 'DISPONIVEL' && d.quantidade > 0;
}

/** Status para exibição: DISPONIVEL sem quantidade é mostrado como em falta. */
export function statusExibido(d: { status: StatusEstoque; quantidade: number }): StatusEstoque {
  return d.status === 'DISPONIVEL' && d.quantidade <= 0 ? 'EM_FALTA' : d.status;
}
