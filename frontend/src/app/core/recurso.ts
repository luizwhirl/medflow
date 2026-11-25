import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, scan, startWith } from 'rxjs';

/**
 * Estado de uma carga da API, para cada tela mostrar o esqueleto, o erro ou os dados
 * (nunca uma área em branco, nem "nenhum resultado" enquanto ainda carrega ou depois de um erro).
 */
export type Recurso<T> =
  | { estado: 'carregando' }
  | { estado: 'erro'; naoEncontrado: boolean }
  | { estado: 'pronto'; dados: T };

/** Começa em `carregando` e termina em `pronto` ou `erro` (404 vira `naoEncontrado`). */
export function carregar<T>(fonte: Observable<T>): Observable<Recurso<T>> {
  return fonte.pipe(
    map((dados): Recurso<T> => ({ estado: 'pronto', dados })),
    catchError((erro: unknown) => {
      // Erro que não veio da API é bug no código: aparece no console em vez de virar "sem conexão".
      if (!(erro instanceof HttpErrorResponse)) console.error(erro);
      return of<Recurso<T>>({
        estado: 'erro',
        naoEncontrado: erro instanceof HttpErrorResponse && erro.status === 404,
      });
    }),
    startWith<Recurso<T>>({ estado: 'carregando' }),
  );
}

export const CARREGANDO: Recurso<never> = { estado: 'carregando' };

/** Os dados, ou `padrao` enquanto carrega ou se deu erro. */
export function dadosOu<T>(recurso: Recurso<T>, padrao: T): T {
  return recurso.estado === 'pronto' ? recurso.dados : padrao;
}

/**
 * Ao recarregar (nova busca, "Tentar de novo"), mantém os dados anteriores na tela em vez de voltar
 * ao esqueleto; o esqueleto fica só para a primeira carga.
 */
export function manterAnterior<T>() {
  return (fonte: Observable<Recurso<T>>): Observable<Recurso<T>> =>
    fonte.pipe(
      scan(
        (anterior: Recurso<T>, atual: Recurso<T>) =>
          atual.estado === 'carregando' && anterior.estado === 'pronto' ? anterior : atual,
        CARREGANDO as Recurso<T>,
      ),
    );
}
