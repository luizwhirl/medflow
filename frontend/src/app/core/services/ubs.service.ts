import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ItemEstoque } from '../models/estoque.model';
import { Ubs } from '../models/ubs.model';

@Injectable({ providedIn: 'root' })
export class UbsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ubs`;

  listar(): Observable<Ubs[]> {
    return this.http.get<Ubs[]>(this.baseUrl);
  }

  buscarPorId(id: number): Observable<Ubs> {
    return this.http.get<Ubs>(`${this.baseUrl}/${id}`);
  }

  estoque(id: number): Observable<ItemEstoque[]> {
    return this.http.get<ItemEstoque[]>(`${this.baseUrl}/${id}/estoque`);
  }
}
