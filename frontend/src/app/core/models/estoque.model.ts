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
