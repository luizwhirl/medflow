import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessaoService } from './sessao.service';

/** Área da gestão: só com sessão de gestor; sem ela, vai para a tela de entrada e volta depois. */
export const gestorGuard: CanActivateFn = (_rota, estado) =>
  inject(SessaoService).ehGestor() ||
  inject(Router).createUrlTree(['/gestao/entrar'], { queryParams: { volta: estado.url } });
