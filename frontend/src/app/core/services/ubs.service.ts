import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { EMPTY, expand, Observable, reduce } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Coordenadas } from '../geo';
import { ItemEstoque } from '../models/estoque.model';
import { Pagina, TAMANHO_MAXIMO_PAGINA } from '../models/pagina.model';
import { Ubs, UbsProxima } from '../models/ubs.model';

@Injectable({ providedIn: 'root' })
export class UbsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ubs`;

  /** Todas as UBS por nome; com `abertas`, só as abertas agora (sem horário cadastrado, ficam de fora). */
  listar(filtro: { abertas?: boolean } = {}): Observable<Ubs[]> {
    const params = filtro.abertas ? new HttpParams().set('abertas', true) : undefined;
    return this.http.get<Ubs[]>(this.baseUrl, { params });
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

  /**
   * Estoque completo da UBS, em ordem de nome. Pede as páginas em sequência (100 itens cada, a maior
   * da API) e junta tudo, para as contagens e o filtro da tela valerem para o estoque inteiro.
   */
  estoque(id: number): Observable<ItemEstoque[]> {
    const pagina = (n: number) =>
      this.http.get<Pagina<ItemEstoque>>(`${this.baseUrl}/${id}/estoque`, {
        params: new HttpParams().set('pagina', n).set('tamanho', TAMANHO_MAXIMO_PAGINA),
      });
    return pagina(0).pipe(
      expand((p) => (p.pagina + 1 < p.totalPaginas ? pagina(p.pagina + 1) : EMPTY)),
      reduce((itens, p) => [...itens, ...p.itens], [] as ItemEstoque[]),
    );
  }
}
