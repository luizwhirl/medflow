import { Injectable, signal } from '@angular/core';

const CHAVE = 'medflow.preferencias';
const MAX_BUSCAS = 3;

interface Preferencias {
  ubsReferenciaId: number | null;
  remediosContinuos: number[];
  buscasRecentes: string[];
}

/**
 * Preferências do cidadão guardadas no navegador.
 * Provisório até existir a API da lista de desejos (PDS-20) e de perfil.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly estado = signal<Preferencias>(this.ler());

  readonly ubsReferenciaId = () => this.estado().ubsReferenciaId;
  readonly remediosContinuos = () => this.estado().remediosContinuos;
  readonly buscasRecentes = () => this.estado().buscasRecentes;

  definirUbsReferencia(id: number | null): void {
    this.atualizar({ ubsReferenciaId: id });
  }

  alternarRemedioContinuo(id: number): void {
    const atuais = this.estado().remediosContinuos;
    this.atualizar({
      remediosContinuos: atuais.includes(id) ? atuais.filter((x) => x !== id) : [...atuais, id],
    });
  }

  /** Guarda o termo no topo das buscas recentes (sem repetir, no máximo 3). */
  registrarBusca(termo: string): void {
    const t = termo.trim();
    if (!t) return;
    const outras = this.estado().buscasRecentes.filter((b) => b.toLowerCase() !== t.toLowerCase());
    this.atualizar({ buscasRecentes: [t, ...outras].slice(0, MAX_BUSCAS) });
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
      const salvo = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as Partial<Preferencias>;
      const lista = <T>(v: unknown, tipo: string): T[] =>
        Array.isArray(v) ? v.filter((x) => typeof x === tipo) : [];
      return {
        ubsReferenciaId: typeof salvo?.ubsReferenciaId === 'number' ? salvo.ubsReferenciaId : null,
        remediosContinuos: lista<number>(salvo?.remediosContinuos, 'number'),
        buscasRecentes: lista<string>(salvo?.buscasRecentes, 'string').slice(0, MAX_BUSCAS),
      };
    } catch {
      return { ubsReferenciaId: null, remediosContinuos: [], buscasRecentes: [] };
    }
  }
}
