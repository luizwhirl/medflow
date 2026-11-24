import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, throwError } from 'rxjs';

import { ListaDesejosService } from './lista-desejos.service';

const CHAVE = 'medflow.preferencias';
const MAX_BUSCAS = 3;

interface Preferencias {
  ubsReferenciaId: number | null;
  buscasRecentes: string[];
  /** Remédios marcados antes da lista de desejos; vão para a API no primeiro acesso. */
  remediosAMigrar: number[];
}

/**
 * Preferências do cidadão.
 *
 * Os remédios de uso contínuo são os medicamentos da lista de desejos do aparelho (API), os
 * mesmos da Minha lista. A UBS de referência e as buscas recentes ficam no navegador: a API não
 * tem perfil do cidadão (sem login, PDS-46 cancelada).
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly listaDesejos = inject(ListaDesejosService);
  private readonly estado = signal<Preferencias>(this.ler());

  readonly ubsReferenciaId = () => this.estado().ubsReferenciaId;
  readonly buscasRecentes = () => this.estado().buscasRecentes;

  /** Medicamentos da lista de desejos, sem repetir (um remédio pode estar em mais de uma UBS). */
  readonly remediosContinuos = computed(
    () => [...new Set((this.listaDesejos.itens() ?? []).map((i) => i.medicamentoId))],
    { equal: (a, b) => a.length === b.length && a.every((id, i) => id === b[i]) },
  );

  constructor() {
    this.listaDesejos.listar().subscribe({
      next: () => this.migrar(),
      error: () => undefined,
    });
  }

  definirUbsReferencia(id: number | null): void {
    this.atualizar({ ubsReferenciaId: id });
    this.migrar();
  }

  /**
   * Adiciona o remédio à lista na UBS de referência (sem aviso) ou, se ele já está na lista,
   * remove-o de todas as UBS. Sem UBS de referência, não há onde adicionar.
   */
  alternarRemedioContinuo(id: number): Observable<unknown> {
    const itens = (this.listaDesejos.itens() ?? []).filter((i) => i.medicamentoId === id);
    if (itens.length) return forkJoin(itens.map((i) => this.listaDesejos.remover(i.id)));
    const ref = this.ubsReferenciaId();
    if (!ref) return throwError(() => new Error('Sem UBS de referência'));
    return this.listaDesejos.adicionar(ref, id, false).pipe(catchError((e) => this.jaNaLista(e)));
  }

  /** Guarda o termo no topo das buscas recentes (sem repetir, no máximo 3). */
  registrarBusca(termo: string): void {
    const t = termo.trim();
    if (!t) return;
    const outras = this.estado().buscasRecentes.filter((b) => b.toLowerCase() !== t.toLowerCase());
    this.atualizar({ buscasRecentes: [t, ...outras].slice(0, MAX_BUSCAS) });
  }

  /**
   * Leva para a lista de desejos os remédios que estavam só no navegador. Espera a lista carregar
   * e haver UBS de referência; o que falhar fica para a próxima vez.
   */
  private migrar(): void {
    const { remediosAMigrar, ubsReferenciaId: ref } = this.estado();
    const lista = this.listaDesejos.itens();
    if (!remediosAMigrar.length || !ref || !lista) return;
    this.atualizar({ remediosAMigrar: [] });
    const faltam = remediosAMigrar.filter((id) => !lista.some((i) => i.medicamentoId === id));
    if (!faltam.length) return;
    forkJoin(
      faltam.map((id) =>
        this.listaDesejos.adicionar(ref, id, false).pipe(
          catchError((e) => this.jaNaLista(e)),
          map(() => null),
          catchError(() => of(id)),
        ),
      ),
    ).subscribe((falhas) => {
      const pendentes = falhas.filter((id): id is number => id !== null);
      if (pendentes.length) this.atualizar({ remediosAMigrar: pendentes });
    });
  }

  /** 409: o item já existe (criado em outra aba); relê a lista para mostrá-lo. */
  private jaNaLista(erro: unknown): Observable<unknown> {
    return erro instanceof HttpErrorResponse && erro.status === 409
      ? this.listaDesejos.listar()
      : throwError(() => erro);
  }

  private atualizar(parcial: Partial<Preferencias>): void {
    this.estado.update((p) => ({ ...p, ...parcial }));
    try {
      localStorage.setItem(CHAVE, JSON.stringify(this.estado()));
    } catch {
      // Armazenamento indisponível (aba privada): mantém só em memória.
    }
  }

  private ler(): Preferencias {
    try {
      const salvo = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as Partial<
        Preferencias & { remediosContinuos: number[] }
      >;
      const lista = <T>(v: unknown, tipo: string): T[] =>
        Array.isArray(v) ? v.filter((x) => typeof x === tipo) : [];
      return {
        ubsReferenciaId: typeof salvo?.ubsReferenciaId === 'number' ? salvo.ubsReferenciaId : null,
        buscasRecentes: lista<string>(salvo?.buscasRecentes, 'string').slice(0, MAX_BUSCAS),
        // `remediosContinuos` é o formato anterior à lista de desejos.
        remediosAMigrar: [
          ...lista<number>(salvo?.remediosAMigrar, 'number'),
          ...lista<number>(salvo?.remediosContinuos, 'number'),
        ],
      };
    } catch {
      return { ubsReferenciaId: null, buscasRecentes: [], remediosAMigrar: [] };
    }
  }
}
