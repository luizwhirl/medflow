import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ItemListaDesejo } from '../models/lista-desejos.model';
import { DispositivoService } from './dispositivo.service';

/**
 * Lista de desejos sem login: a API identifica o dono pelo cabeçalho `X-Dispositivo-Id`,
 * enviado só nestas chamadas.
 */
@Injectable({ providedIn: 'root' })
export class ListaDesejosService {
  private readonly http = inject(HttpClient);
  private readonly dispositivo = inject(DispositivoService);
  private readonly baseUrl = `${environment.apiUrl}/lista-desejos`;

  private get headers(): HttpHeaders {
    return new HttpHeaders({ 'X-Dispositivo-Id': this.dispositivo.identificador() });
  }

  /** Do mais recente para o mais antigo. */
  listar(): Observable<ItemListaDesejo[]> {
    return this.http.get<ItemListaDesejo[]>(this.baseUrl, { headers: this.headers });
  }

  adicionar(ubsId: number, medicamentoId: number, notificar = true): Observable<ItemListaDesejo> {
    return this.http.post<ItemListaDesejo>(
      this.baseUrl,
      { ubsId, medicamentoId, notificar },
      { headers: this.headers },
    );
  }

  definirAviso(id: number, notificar: boolean): Observable<ItemListaDesejo> {
    return this.http.patch<ItemListaDesejo>(
      `${this.baseUrl}/${id}`,
      { notificar },
      { headers: this.headers },
    );
  }

  remover(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { headers: this.headers });
  }
}
