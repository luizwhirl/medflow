import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';

import { AvisosService } from '../services/avisos.service';

/**
 * Abrir a tela de avisos marca todos como lidos. Não segura a navegação: se a API falhar, a tela
 * abre do mesmo jeito e o contador continua como estava.
 */
export const marcarAvisosComoLidos: CanActivateFn = () => {
  // Sempre chama a API: abrindo direto em /avisos, o contador do menu pode ainda não ter chegado.
  inject(AvisosService)
    .marcarComoLidos()
    .subscribe({ error: () => undefined });
  return true;
};
