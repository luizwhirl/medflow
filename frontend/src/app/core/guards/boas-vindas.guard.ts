import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { PreferenciasService } from '../services/preferencias.service';

/**
 * Na primeira visita, leva à boas-vindas e guarda em `voltar` a rota pedida,
 * para quem abriu um link (ex.: /ubs/3) continuar nela depois.
 */
export const exigirBoasVindas: CanActivateFn = (_rota, estado) => {
  if (inject(PreferenciasService).boasVindasConcluidas()) return true;
  return inject(Router).createUrlTree(['/boas-vindas'], {
    queryParams: estado.url === '/' ? {} : { voltar: estado.url },
  });
};

/** Só aceita rota interna do app em `voltar` (nada de `//site.com` ou URL absoluta). */
export function destinoSeguro(voltar: string | null | undefined): string {
  return voltar && voltar.startsWith('/') && !voltar.startsWith('//') ? voltar : '/';
}
