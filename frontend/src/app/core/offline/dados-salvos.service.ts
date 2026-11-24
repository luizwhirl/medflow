import { Injectable } from '@angular/core';

/** Prefixo das respostas salvas no navegador (o "v1" permite descartar tudo se o formato mudar). */
export const PREFIXO_DADOS_SALVOS = 'medflow.dados.v1:';

export interface DadoSalvo {
  /** Quando a resposta chegou da API (ms desde 1970). */
  em: number;
  corpo: unknown;
}

/**
 * Última resposta de cada consulta à API, guardada no navegador para as telas não ficarem em branco
 * sem internet. Só leituras (GET) passam por aqui; nada é enviado para fora do aparelho.
 */
@Injectable({ providedIn: 'root' })
export class DadosSalvosService {
  ler(chave: string): DadoSalvo | null {
    try {
      const bruto = localStorage.getItem(PREFIXO_DADOS_SALVOS + chave);
      if (!bruto) return null;
      const dado = JSON.parse(bruto) as Partial<DadoSalvo>;
      return typeof dado.em === 'number' && 'corpo' in dado
        ? { em: dado.em, corpo: dado.corpo }
        : null;
    } catch {
      return null;
    }
  }

  salvar(chave: string, corpo: unknown, em = Date.now()): void {
    const valor = JSON.stringify({ em, corpo } satisfies DadoSalvo);
    try {
      localStorage.setItem(PREFIXO_DADOS_SALVOS + chave, valor);
    } catch {
      // Espaço cheio: descarta as respostas salvas mais antigas e tenta mais uma vez.
      this.liberarEspaco();
      try {
        localStorage.setItem(PREFIXO_DADOS_SALVOS + chave, valor);
      } catch {
        // Sem armazenamento (aba privada ou cota esgotada): segue sem salvar.
      }
    }
  }

  /** Remove as respostas de `recurso` e de tudo abaixo dele (`/api/x`, `/api/x?…`, `/api/x/1`). */
  descartar(recurso: string): void {
    this.remover(
      this.chaves().filter(
        (c) => c === recurso || c.startsWith(`${recurso}/`) || c.startsWith(`${recurso}?`),
      ),
    );
  }

  /** Remove a metade mais antiga das respostas salvas. */
  private liberarEspaco(): void {
    const salvas = this.chaves().map((chave) => ({ chave, em: this.ler(chave)?.em ?? 0 }));
    this.remover(
      salvas
        .sort((a, b) => a.em - b.em)
        .slice(0, Math.ceil(salvas.length / 2))
        .map(({ chave }) => chave),
    );
  }

  /** Chaves salvas, sem o prefixo. */
  private chaves(): string[] {
    const chaves: string[] = [];
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const chave = localStorage.key(i);
        if (chave?.startsWith(PREFIXO_DADOS_SALVOS)) {
          chaves.push(chave.slice(PREFIXO_DADOS_SALVOS.length));
        }
      }
    } catch {
      // Armazenamento indisponível.
    }
    return chaves;
  }

  private remover(chaves: string[]): void {
    try {
      chaves.forEach((c) => localStorage.removeItem(PREFIXO_DADOS_SALVOS + c));
    } catch {
      // Armazenamento indisponível.
    }
  }
}
