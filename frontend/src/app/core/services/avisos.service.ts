import { HttpClient, HttpHeaders } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Aviso } from '../models/aviso.model';
import { DispositivoService } from './dispositivo.service';

export type EstadoAvisos =
  { tipo: 'carregando' } | { tipo: 'erro' } | { tipo: 'ok'; avisos: Aviso[]; naoLidos: number };

/**
 * Avisos deste aparelho, gerados pela API (cabeçalho `X-Dispositivo-Id`, o mesmo da lista de
 * desejos) e compartilhados pela tela de Avisos, pelo contador do menu e pelo cartão da Início.
 * A lista traz só os 50 mais recentes; o total de não lidos vem de uma contagem à parte.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly http = inject(HttpClient);
  private readonly dispositivo = inject(DispositivoService);
  private readonly baseUrl = `${environment.apiUrl}/avisos`;

  private readonly estadoAtual = signal<EstadoAvisos>({ tipo: 'carregando' });
  readonly estado = this.estadoAtual.asReadonly();

  readonly avisos = computed(() => {
    const e = this.estadoAtual();
    return e.tipo === 'ok' ? e.avisos : [];
  });

  readonly naoLidos = computed(() => {
    const e = this.estadoAtual();
    return e.tipo === 'ok' ? e.naoLidos : 0;
  });

  private leitura?: Subscription;

  constructor() {
    this.recarregar();
  }

  private get headers(): HttpHeaders {
    return new HttpHeaders({ 'X-Dispositivo-Id': this.dispositivo.identificador() });
  }

  recarregar(): void {
    this.leitura?.unsubscribe();
    this.estadoAtual.set({ tipo: 'carregando' });
    this.leitura = forkJoin({
      avisos: this.http.get<Aviso[]>(this.baseUrl, { headers: this.headers }),
      total: this.http.get<{ naoLidos: number }>(`${this.baseUrl}/nao-lidos/total`, {
        headers: this.headers,
      }),
    }).subscribe({
      next: ({ avisos, total }) =>
        this.estadoAtual.set({ tipo: 'ok', avisos, naoLidos: total.naoLidos }),
      error: () => this.estadoAtual.set({ tipo: 'erro' }),
    });
  }

  /** A API só marca todos de uma vez; se falhar, a lista continua como estava. */
  marcarTodosComoLidos(): void {
    this.http
      .patch<{ atualizados: number }>(`${this.baseUrl}/lidas`, null, { headers: this.headers })
      .subscribe({
        next: () =>
          this.estadoAtual.update((e) =>
            e.tipo === 'ok'
              ? { tipo: 'ok', avisos: e.avisos.map((a) => ({ ...a, lido: true })), naoLidos: 0 }
              : e,
          ),
        error: () => undefined,
      });
  }
}
