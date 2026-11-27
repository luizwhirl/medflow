import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { finalize, Observable, share, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ItemListaDesejo } from '../models/lista-desejos.model';
import { DispositivoService } from './dispositivo.service';

/**
 * Lista de desejos sem login: a API identifica o dono pelo cabeçalho `X-Dispositivo-Id`,
 * enviado só nestas chamadas.
 *
 * Guarda a última lista recebida em `itens`, atualizada a cada leitura e alteração, para que
 * Início, Buscar, Mapa e Minha lista mostrem os mesmos dados sem recarregar.
 */
@Injectable({ providedIn: 'root' })
export class ListaDesejosService {
  private readonly http = inject(HttpClient);
  private readonly dispositivo = inject(DispositivoService);
  private readonly baseUrl = `${environment.apiUrl}/lista-desejos`;

  private readonly estado = signal<ItemListaDesejo[] | null | undefined>(undefined);
  /** Lista do aparelho: `undefined` antes da primeira leitura, `null` se ela falhou. */
  readonly itens = this.estado.asReadonly();

  private get headers(): HttpHeaders {
    return new HttpHeaders({ 'X-Dispositivo-Id': this.dispositivo.identificador() });
  }

  /** Leitura em andamento, compartilhada por quem pedir a lista ao mesmo tempo. */
  private lendo: Observable<ItemListaDesejo[]> | null = null;

  /** Do mais recente para o mais antigo. */
  listar(): Observable<ItemListaDesejo[]> {
    return (this.lendo ??= this.http
      .get<ItemListaDesejo[]>(this.baseUrl, { headers: this.headers })
      .pipe(
        tap({
          next: (itens) => this.estado.set(itens),
          error: () => this.estado.update((atual) => atual ?? null),
        }),
        finalize(() => (this.lendo = null)),
        share(),
      ));
  }

  adicionar(ubsId: number, medicamentoId: number, notificar = true): Observable<ItemListaDesejo> {
    return this.http
      .post<ItemListaDesejo>(
        this.baseUrl,
        { ubsId, medicamentoId, notificar },
        { headers: this.headers },
      )
      .pipe(tap((item) => this.guardar(item)));
  }

  definirAviso(id: number, notificar: boolean): Observable<ItemListaDesejo> {
    return this.http
      .patch<ItemListaDesejo>(`${this.baseUrl}/${id}`, { notificar }, { headers: this.headers })
      .pipe(tap((item) => this.guardar(item)));
  }

  remover(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${id}`, { headers: this.headers })
      .pipe(tap(() => this.estado.update((lista) => lista?.filter((i) => i.id !== id) ?? lista)));
  }

  /** Item novo vai para o topo; um já existente é trocado no lugar. */
  private guardar(item: ItemListaDesejo): void {
    this.estado.update((lista) => {
      if (!lista) return lista;
      return lista.some((i) => i.id === item.id)
        ? lista.map((i) => (i.id === item.id ? item : i))
        : [item, ...lista];
    });
  }
}
