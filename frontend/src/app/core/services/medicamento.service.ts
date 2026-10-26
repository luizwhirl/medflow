import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Disponibilidade } from '../models/estoque.model';
import { Medicamento } from '../models/medicamento.model';

@Injectable({ providedIn: 'root' })
export class MedicamentoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/medicamentos`;

  buscar(termo = ''): Observable<Medicamento[]> {
    const params = termo ? new HttpParams().set('busca', termo) : undefined;
    return this.http.get<Medicamento[]>(this.baseUrl, { params });
  }

  buscarPorId(id: number): Observable<Medicamento> {
    return this.http.get<Medicamento>(`${this.baseUrl}/${id}`);
  }

  disponibilidade(id: number): Observable<Disponibilidade[]> {
    return this.http.get<Disponibilidade[]>(`${this.baseUrl}/${id}/disponibilidade`);
  }
}
