import { HttpClient, HttpParams } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, map, Observable, of, startWith, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Aviso } from '../models/aviso.model';
import { PreferenciasService } from './preferencias.service';

const CHAVE_LIDOS = 'medflow.avisos-lidos';

export type EstadoAvisos =
  { tipo: 'sem-ubs' } | { tipo: 'carregando' } | { tipo: 'erro' } | { tipo: 'ok'; avisos: Aviso[] };

export interface AvisoComLeitura extends Aviso {
  lido: boolean;
}

/**
 * Avisos da UBS de referência para os remédios de uso contínuo, compartilhados pela tela de
 * Avisos, pelo contador do menu e pelo cartão da Início (uma chamada para todos).
 * O que já foi lido fica guardado neste navegador, pelo `id` estável de cada aviso.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly http = inject(HttpClient);
  private readonly preferencias = inject(PreferenciasService);
  private readonly baseUrl = `${environment.apiUrl}/avisos`;

  private readonly tentativa = signal(0);
  private readonly lidos = signal<ReadonlySet<string>>(new Set(lerLidos()));

  readonly estado = toSignal(
    combineLatest([
      toObservable(
        computed(() => ({
          ubsId: this.preferencias.ubsReferenciaId(),
          medicamentoIds: this.preferencias.remediosContinuos(),
        })),
      ),
      toObservable(this.tentativa),
    ]).pipe(
      switchMap(([{ ubsId, medicamentoIds }]) =>
        ubsId === null
          ? of<EstadoAvisos>({ tipo: 'sem-ubs' })
          : this.listar(ubsId, medicamentoIds).pipe(
              map((avisos): EstadoAvisos => {
                this.esquecerLidosQueSumiram(avisos);
                return { tipo: 'ok', avisos };
              }),
              catchError(() => of<EstadoAvisos>({ tipo: 'erro' })),
              startWith<EstadoAvisos>({ tipo: 'carregando' }),
            ),
      ),
    ),
    { initialValue: { tipo: 'carregando' } as EstadoAvisos },
  );

  readonly avisos = computed((): AvisoComLeitura[] => {
    const e = this.estado();
    const lidos = this.lidos();
    return e.tipo === 'ok' ? e.avisos.map((a) => ({ ...a, lido: lidos.has(a.id) })) : [];
  });

  readonly naoLidos = computed(() => this.avisos().filter((a) => !a.lido).length);

  listar(ubsId: number, medicamentoIds: readonly number[]): Observable<Aviso[]> {
    let params = new HttpParams().set('ubsId', ubsId);
    if (medicamentoIds.length) params = params.set('medicamentoIds', medicamentoIds.join(','));
    return this.http.get<Aviso[]>(this.baseUrl, { params });
  }

  recarregar(): void {
    this.tentativa.update((n) => n + 1);
  }

  marcarComoLido(id: string): void {
    if (this.lidos().has(id)) return;
    this.salvarLidos(new Set(this.lidos()).add(id));
  }

  marcarTodosComoLidos(): void {
    this.salvarLidos(new Set([...this.lidos(), ...this.avisos().map((a) => a.id)]));
  }

  /** Mantém só os lidos que ainda existem, para o armazenamento não crescer para sempre. */
  private esquecerLidosQueSumiram(avisos: Aviso[]): void {
    const atuais = new Set(avisos.map((a) => a.id));
    const mantidos = [...this.lidos()].filter((id) => atuais.has(id));
    if (mantidos.length !== this.lidos().size) this.salvarLidos(new Set(mantidos));
  }

  private salvarLidos(lidos: ReadonlySet<string>): void {
    this.lidos.set(lidos);
    try {
      localStorage.setItem(CHAVE_LIDOS, JSON.stringify([...lidos]));
    } catch {
      // Armazenamento indisponível (aba privada): vale só enquanto a aba estiver aberta.
    }
  }
}

function lerLidos(): string[] {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE_LIDOS) ?? '[]');
    return Array.isArray(salvo) ? salvo.filter((x) => typeof x === 'string') : [];
  } catch {
    return [];
  }
}
