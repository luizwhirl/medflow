import { Injectable } from '@angular/core';

/** Prefixo das respostas salvas no navegador (o "v1" permite descartar tudo se o formato mudar). */
export const PREFIXO_DADOS_SALVOS = 'medflow.dados.v1:';

/**
 * Cada termo digitado vira uma chave (`/api/medicamentos?busca=…`); guarda só as mais recentes,
 * para o armazenamento não crescer até a cota e o descarte em massa não levar o que ainda é usado.
 */
export const MAX_BUSCAS_SALVAS = 50;

/**
 * Limite de respostas salvas no total (detalhes de medicamento, disponibilidade, UBS…). Acima
 * dele, saem as mais antigas: assim o armazenamento não chega à cota, e as preferências e o
 * identificador do aparelho continuam conseguindo ser gravados.
 */
export const MAX_RESPOSTAS_SALVAS = 200;

/** Dados com mais de 7 dias não são mostrados: estoque e remessas mudam muito nesse tempo. */
export const IDADE_MAXIMA_MS = 7 * 24 * 60 * 60 * 1000;

const ehBusca = (chave: string) => /[?&]busca=/.test(chave);

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
  /** A resposta salva, ou `null` se não houver, estiver corrompida ou passar de 7 dias. */
  ler(chave: string, agora = Date.now()): DadoSalvo | null {
    const dado = this.lerBruto(chave);
    if (dado && agora - dado.em > IDADE_MAXIMA_MS) {
      this.remover([chave]);
      return null;
    }
    return dado;
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
    if (ehBusca(chave)) this.limitar(this.chaves().filter(ehBusca), MAX_BUSCAS_SALVAS);
    this.limitar(this.chaves(), MAX_RESPOSTAS_SALVAS);
  }

  /** Remove as respostas de `recurso` e de tudo abaixo dele (`/api/x`, `/api/x?…`, `/api/x/1`). */
  descartar(recurso: string): void {
    this.remover(
      this.chaves().filter(
        (c) => c === recurso || c.startsWith(`${recurso}/`) || c.startsWith(`${recurso}?`),
      ),
    );
  }

  /** Mantém só as `maximo` mais recentes entre `chaves`. */
  private limitar(chaves: string[], maximo: number): void {
    if (chaves.length <= maximo) return;
    this.remover(
      this.maisRecentesPrimeiro(chaves)
        .slice(maximo)
        .map(({ chave }) => chave),
    );
  }

  /** Remove a metade mais antiga das respostas salvas. */
  private liberarEspaco(): void {
    const salvas = this.maisRecentesPrimeiro(this.chaves());
    this.remover(salvas.slice(Math.floor(salvas.length / 2)).map(({ chave }) => chave));
  }

  private maisRecentesPrimeiro(chaves: string[]): { chave: string; em: number }[] {
    return chaves
      .map((chave) => ({ chave, em: this.lerBruto(chave)?.em ?? 0 }))
      .sort((a, b) => b.em - a.em);
  }

  private lerBruto(chave: string): DadoSalvo | null {
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
