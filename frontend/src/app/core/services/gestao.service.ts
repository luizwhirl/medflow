import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { PainelGestao } from '../models/gestao.model';

/**
 * Indicadores do painel da gestão.
 *
 * TODO(PDS-21): trocar os dados de demonstração pela API quando o endpoint existir:
 *   return this.http.get<PainelGestao>(`${environment.apiUrl}/gestao/painel`);
 */
@Injectable({ providedIn: 'root' })
export class GestaoService {
  painel(): Observable<PainelGestao> {
    return of(painelDemonstracao(new Date()));
  }
}

/** Dados fictícios no formato da API, com datas relativas a `hoje`. */
export function painelDemonstracao(hoje: Date): PainelGestao {
  const emDias = (dias: number) => {
    const d = new Date(hoje);
    d.setDate(d.getDate() + dias);
    // Data local (toISOString usaria UTC e poderia virar o dia à noite).
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  };

  return {
    ubs: { id: 1, nome: 'UBS Benedito Bentes I', cnes: '9000101' },
    ultimaSincronizacao: new Date(hoje.getTime() - 4 * 60_000).toISOString(),
    indicadores: {
      itensEmFalta: 6,
      emFaltaComRemessa: 2,
      abaixoDoMinimo: 14,
      prazoReposicaoDias: 7,
      remessasACaminho: 2,
      proximaRemessa: emDias(3),
      pacientesAguardando: 87,
      medicamentosAguardados: 9,
    },
    itensAtencao: [
      {
        medicamentoId: 1,
        medicamento: 'Metformina 850 mg',
        estoque: 0,
        minimo: 400,
        aguardandoAviso: 42,
        situacao: 'EM_FALTA',
        remessaPrevista: emDias(3),
      },
      {
        medicamentoId: 7,
        medicamento: 'Insulina humana NPH 100 UI/mL',
        estoque: 0,
        minimo: 60,
        aguardandoAviso: 19,
        situacao: 'EM_FALTA',
        remessaPrevista: emDias(5),
      },
      {
        medicamentoId: 4,
        medicamento: 'Sinvastatina 20 mg',
        estoque: 85,
        minimo: 300,
        aguardandoAviso: 12,
        situacao: 'ABAIXO_MINIMO',
        remessaPrevista: null,
      },
      {
        medicamentoId: 5,
        medicamento: 'Omeprazol 20 mg',
        estoque: 140,
        minimo: 250,
        aguardandoAviso: 4,
        situacao: 'ABAIXO_MINIMO',
        remessaPrevista: null,
      },
      {
        medicamentoId: 3,
        medicamento: 'Glibenclamida 5 mg',
        estoque: 210,
        minimo: 200,
        aguardandoAviso: 0,
        situacao: 'PEDIDO_ENVIADO',
        remessaPrevista: null,
      },
    ],
    maisProcurados: [
      { medicamento: 'Metformina 850 mg', buscas: 42 },
      { medicamento: 'Losartana 50 mg', buscas: 31 },
      { medicamento: 'Sinvastatina 20 mg', buscas: 20 },
      { medicamento: 'Insulina NPH', buscas: 19 },
      { medicamento: 'Omeprazol 20 mg', buscas: 9 },
    ],
    ficticio: true,
  };
}
