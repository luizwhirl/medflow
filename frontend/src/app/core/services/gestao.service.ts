import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Coordenadas } from '../geo';
import {
  DemandaReprimidaResponse,
  IndicadoresResponse,
  ItemAtencaoResponse,
  LocalizacaoUbs,
  PainelGestao,
} from '../models/gestao.model';

/** Quantos itens e "mais procurados" o painel mostra. */
const LIMITE_ITENS = 50;
const LIMITE_MAIS_PROCURADOS = 5;

/** Indicadores do painel da gestão, no escopo do gestor logado (a UBS dele ou a rede toda). */
@Injectable({ providedIn: 'root' })
export class GestaoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/gestao`;

  painel(ubsId?: number | null): Observable<PainelGestao> {
    const escopo = ubsId != null ? new HttpParams().set('ubsId', ubsId) : new HttpParams();
    return forkJoin({
      indicadores: this.http.get<IndicadoresResponse>(`${this.baseUrl}/indicadores`, {
        params: escopo,
      }),
      itens: this.http.get<ItemAtencaoResponse[]>(`${this.baseUrl}/itens-atencao`, {
        params: escopo.set('limite', LIMITE_ITENS),
      }),
      demanda: this.http.get<DemandaReprimidaResponse>(`${this.baseUrl}/demanda-reprimida`, {
        params: escopo.set('limite', LIMITE_MAIS_PROCURADOS),
      }),
    }).pipe(map(({ indicadores, itens, demanda }) => montarPainel(indicadores, itens, demanda)));
  }

  localizacao(ubsId: number): Observable<LocalizacaoUbs> {
    return this.http.get<LocalizacaoUbs>(`${this.baseUrl}/ubs/${ubsId}/localizacao`);
  }

  corrigirLocalizacao(ubsId: number, ponto: Coordenadas): Observable<LocalizacaoUbs> {
    return this.http.put<LocalizacaoUbs>(`${this.baseUrl}/ubs/${ubsId}/localizacao`, ponto);
  }
}

export function montarPainel(
  { ubs, restrito, ultimaSincronizacao, ...indicadores }: IndicadoresResponse,
  itens: ItemAtencaoResponse[],
  demanda: DemandaReprimidaResponse,
): PainelGestao {
  return {
    ubs,
    restrito,
    ultimaSincronizacao,
    indicadores,
    itensAtencao: itens.map((i) => ({
      ubsId: i.ubs.id,
      ubs: i.ubs.nome,
      medicamentoId: i.medicamento.id,
      medicamento: i.medicamento.nome,
      estoque: i.estoque,
      minimo: i.minimo,
      aguardandoAviso: i.aguardandoAviso,
      situacao: i.situacao,
      remessaPrevista: i.remessaPrevista,
    })),
    maisProcurados: demanda.maisProcurados.map((m) => ({
      medicamentoId: m.medicamento.id,
      medicamento: m.medicamento.nome,
      buscas: m.buscas,
    })),
  };
}
