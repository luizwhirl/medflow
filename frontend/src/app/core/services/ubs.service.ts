import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Coordenadas } from '../geo';
import { ItemEstoque } from '../models/estoque.model';
import { Ubs, UbsProxima } from '../models/ubs.model';

@Injectable({ providedIn: 'root' })
export class UbsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ubs`;

  listar(): Observable<Ubs[]> {
    return this.http.get<Ubs[]>(this.baseUrl);
  }

  /**
   * UBS a até `raioKm` de `origem`, da mais próxima para a mais distante (sem `raioKm`, a API usa
   * 20 km). Com `medicamentoId`, as unidades que têm o item trazem `status` e `quantidade`.
   */
  proximas(
    origem: Coordenadas,
    filtro: { raioKm?: number | null; medicamentoId?: number } = {},
  ): Observable<UbsProxima[]> {
    let params = new HttpParams().set('lat', origem.latitude).set('lng', origem.longitude);
    if (filtro.raioKm != null) params = params.set('raioKm', filtro.raioKm);
    if (filtro.medicamentoId != null) params = params.set('medicamentoId', filtro.medicamentoId);
    return this.http.get<UbsProxima[]>(this.baseUrl, { params });
  }

  /** Bairros que têm UBS, em ordem alfabética (boas-vindas sem localização). */
  bairros(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/bairros`);
  }

  buscarPorId(id: number): Observable<Ubs> {
    return this.http.get<Ubs>(`${this.baseUrl}/${id}`);
  }

  estoque(id: number): Observable<ItemEstoque[]> {
    return this.http.get<ItemEstoque[]>(`${this.baseUrl}/${id}/estoque`);
  }
}
