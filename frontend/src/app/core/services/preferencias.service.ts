import { Injectable, signal } from '@angular/core';

const CHAVE = 'medflow.preferencias';

interface Preferencias {
  ubsReferenciaId: number | null;
  remediosContinuos: number[];
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

  definirUbsReferencia(id: number | null): void {
    this.atualizar({ ubsReferenciaId: id });
  }

  alternarRemedioContinuo(id: number): void {
    const atuais = this.estado().remediosContinuos;
    this.atualizar({
      remediosContinuos: atuais.includes(id) ? atuais.filter((x) => x !== id) : [...atuais, id],
    });
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
      return {
        ubsReferenciaId: typeof salvo?.ubsReferenciaId === 'number' ? salvo.ubsReferenciaId : null,
        remediosContinuos: Array.isArray(salvo?.remediosContinuos)
          ? salvo.remediosContinuos.filter((x) => typeof x === 'number')
          : [],
      };
    } catch {
      return { ubsReferenciaId: null, remediosContinuos: [] };
    }
  }
}
