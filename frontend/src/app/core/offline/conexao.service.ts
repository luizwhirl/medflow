import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';

/**
 * Situação da conexão vista pelas telas:
 * - `online`: o navegador diz se há rede (eventos `online`/`offline`);
 * - `dadosSalvosEm`: quando a tela atual está mostrando respostas salvas (sem rede ou com a API
 *   fora do ar), o horário da mais antiga delas. Volta a `null` ao trocar de tela.
 */
@Injectable({ providedIn: 'root' })
export class ConexaoService {
  private readonly janela = inject(DOCUMENT).defaultView;

  private readonly navegadorOnline = signal(this.janela?.navigator.onLine ?? true);
  private readonly salvoEm = signal<number | null>(null);

  readonly online = this.navegadorOnline.asReadonly();
  readonly dadosSalvosEm = this.salvoEm.asReadonly();

  constructor() {
    const atualizar = () => this.navegadorOnline.set(this.janela?.navigator.onLine ?? true);
    this.janela?.addEventListener('online', atualizar);
    this.janela?.addEventListener('offline', atualizar);
    inject(DestroyRef).onDestroy(() => {
      this.janela?.removeEventListener('online', atualizar);
      this.janela?.removeEventListener('offline', atualizar);
    });

    // Mudar só os parâmetros (?id=, filtros) não é outra tela: o aviso continua valendo.
    let caminho: string | null = null;
    inject(Router).events.subscribe((evento) => {
      if (!(evento instanceof NavigationStart)) return;
      const novo = evento.url.split(/[?#]/)[0];
      if (novo !== caminho) this.salvoEm.set(null);
      caminho = novo;
    });
  }

  /** Uma resposta salva em `em` foi usada no lugar da API. */
  registrarDadosSalvos(em: number): void {
    this.salvoEm.update((atual) => (atual === null ? em : Math.min(atual, em)));
  }

  /** Recarrega a página para buscar tudo de novo na API. */
  tentarDeNovo(): void {
    this.janela?.location.reload();
  }
}
