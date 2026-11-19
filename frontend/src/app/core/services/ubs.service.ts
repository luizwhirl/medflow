import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Coordenadas } from '../geo';
import { ItemEstoque } from '../models/estoque.model';
import { Pagina, TAMANHO_MAXIMO_PAGINA } from '../models/pagina.model';
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

  buscarPorId(id: number): Observable<Ubs> {
    return this.http.get<Ubs>(`${this.baseUrl}/${id}`);
  }

  /** Estoque da UBS em ordem de nome (até 100 itens: a maior página da API). */
  estoque(id: number): Observable<ItemEstoque[]> {
    const params = new HttpParams().set('tamanho', TAMANHO_MAXIMO_PAGINA);
    return this.http
      .get<Pagina<ItemEstoque>>(`${this.baseUrl}/${id}/estoque`, { params })
      .pipe(map((pagina) => pagina.itens));
  }
}
