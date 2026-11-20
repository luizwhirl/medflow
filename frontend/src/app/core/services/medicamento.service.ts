import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Disponibilidade } from '../models/estoque.model';
import { Medicamento } from '../models/medicamento.model';
import { Pagina } from '../models/pagina.model';

@Injectable({ providedIn: 'root' })
export class MedicamentoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/medicamentos`;

  /**
   * Primeira página da busca, em ordem de nome, com o total encontrado.
   * Sem `tamanho`, a API devolve 20 itens (no máximo, `TAMANHO_MAXIMO_PAGINA`).
   */
  buscar(termo = '', categoria = '', tamanho?: number): Observable<Pagina<Medicamento>> {
    let params = new HttpParams();
    if (termo) params = params.set('busca', termo);
    if (categoria) params = params.set('categoria', categoria);
    if (tamanho != null) params = params.set('tamanho', tamanho);
    return this.http.get<Pagina<Medicamento>>(this.baseUrl, { params });
  }

  categorias(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/categorias`);
  }

  buscarPorId(id: number): Observable<Medicamento> {
    return this.http.get<Medicamento>(`${this.baseUrl}/${id}`);
  }

  disponibilidade(id: number): Observable<Disponibilidade[]> {
    return this.http.get<Disponibilidade[]>(`${this.baseUrl}/${id}/disponibilidade`);
  }
}
