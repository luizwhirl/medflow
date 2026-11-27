import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Aviso } from '../models/aviso.model';
import { DispositivoService } from './dispositivo.service';

/**
 * Avisos do aparelho (`/api/avisos`), identificados como a lista de desejos pelo cabeçalho
 * `X-Dispositivo-Id`. Guarda os últimos avisos e o total de não lidos em signals, para o menu, a
 * Início e a tela de avisos mostrarem o mesmo número.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly http = inject(HttpClient);
  private readonly dispositivo = inject(DispositivoService);
  private readonly baseUrl = `${environment.apiUrl}/avisos`;

  private readonly lista = signal<Aviso[] | null | undefined>(undefined);
  private readonly total = signal(0);

  /** Até 50 avisos, do mais recente; `undefined` antes da primeira leitura, `null` se ela falhou. */
  readonly avisos = this.lista.asReadonly();
  /** Total de não lidos, contado na API (inclui os que passam dos 50 da lista). */
  readonly naoLidos = this.total.asReadonly();

  private get headers(): HttpHeaders {
    return new HttpHeaders({ 'X-Dispositivo-Id': this.dispositivo.identificador() });
  }

  listar(): Observable<Aviso[]> {
    return this.http.get<Aviso[]>(this.baseUrl, { headers: this.headers }).pipe(
      tap({
        next: (avisos) => this.lista.set(avisos),
        error: () => this.lista.update((atual) => atual ?? null),
      }),
    );
  }

  /** Atualiza o total de não lidos; sem resposta da API, o número fica como estava. */
  contarNaoLidos(): void {
    this.http
      .get<{ naoLidos: number }>(`${this.baseUrl}/nao-lidos/total`, { headers: this.headers })
      .subscribe({ next: ({ naoLidos }) => this.total.set(naoLidos), error: () => undefined });
  }

  /** A API só marca todos de uma vez (`PATCH /api/avisos/lidas`). */
  marcarComoLidos(): Observable<number> {
    return this.http
      .patch<{ atualizados: number }>(`${this.baseUrl}/lidas`, null, { headers: this.headers })
      .pipe(
        map(({ atualizados }) => atualizados),
        tap(() => {
          this.total.set(0);
          this.lista.update((avisos) => avisos?.map((a) => ({ ...a, lido: true })) ?? avisos);
        }),
      );
  }
}
