import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SessaoService } from './sessao.service';

/** Rotas da API que pedem o token: a área da gestão e a conta de quem entrou. */
function precisaDeToken(url: string): boolean {
  const base = environment.apiUrl;
  return url.startsWith(`${base}/gestao`) || url === `${base}/auth/eu`;
}

/**
 * Manda o token nas rotas da gestão. Se a API responder 401 (token vencido ou conta removida), encerra a sessão e
 * leva para a tela de entrada, voltando para onde a pessoa estava depois de entrar.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!precisaDeToken(req.url)) return next(req);

  const sessao = inject(SessaoService);
  const router = inject(Router);
  const token = sessao.token();
  const pedido = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(pedido).pipe(
    catchError((erro: unknown) => {
      if (erro instanceof HttpErrorResponse && erro.status === 401) {
        sessao.sair();
        void router.navigate(['/gestao/entrar'], { queryParams: { volta: router.url } });
      }
      return throwError(() => erro);
    }),
  );
};
