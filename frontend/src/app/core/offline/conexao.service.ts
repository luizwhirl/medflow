import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';

import { environment } from '../../../environments/environment';

/**
 * Situação da conexão vista pelas telas:
 * - `online`: o navegador diz se há rede (eventos `online`/`offline`);
 * - `dadosSalvosEm`: quando a tela atual está mostrando respostas salvas (sem rede ou com a API
 *   fora do ar), o horário da mais antiga delas. Volta a `null` ao trocar de tela ou quando todas
 *   as consultas que usaram dados salvos voltam a responder.
 */
@Injectable({ providedIn: 'root' })
export class ConexaoService {
  private readonly janela = inject(DOCUMENT).defaultView;

  private readonly navegadorOnline = signal(this.janela?.navigator.onLine ?? true);
  private readonly salvoEm = signal<number | null>(null);
  private readonly verificandoApi = signal(false);
  private readonly apiSemResposta = signal(false);
  /** Consultas da tela atual que estão mostrando dados salvos (pela chave do interceptor). */
  private readonly comDadosSalvos = new Set<string>();

  readonly online = this.navegadorOnline.asReadonly();
  readonly dadosSalvosEm = this.salvoEm.asReadonly();
  /** "Atualizar" está conferindo se a API voltou. */
  readonly verificando = this.verificandoApi.asReadonly();
  /** O último "Atualizar" não encontrou a API (a página não foi recarregada). */
  readonly semResposta = this.apiSemResposta.asReadonly();

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
      if (novo !== caminho) this.limpar();
      caminho = novo;
    });
  }

  /** Uma resposta salva em `em` foi usada no lugar da API (na consulta `chave`, se informada). */
  registrarDadosSalvos(em: number, chave?: string): void {
    if (chave) this.comDadosSalvos.add(chave);
    this.salvoEm.update((atual) => (atual === null ? em : Math.min(atual, em)));
  }

  /**
   * A consulta `chave` voltou a responder (ex.: "Tentar de novo" na tela). Quando nenhuma consulta
   * da tela depende mais de dados salvos, o aviso some.
   */
  registrarAtualizado(chave: string): void {
    if (!this.comDadosSalvos.delete(chave) || this.comDadosSalvos.size > 0) return;
    this.salvoEm.set(null);
    this.apiSemResposta.set(false);
  }

  /**
   * Confere se a API responde antes de recarregar a página. `navigator.onLine` pode dizer que há
   * rede num Wi-Fi sem internet; recarregar ali trocaria o app pela página de erro do navegador.
   */
  async tentarDeNovo(): Promise<void> {
    if (this.verificandoApi()) return;
    this.verificandoApi.set(true);
    this.apiSemResposta.set(false);
    try {
      const resposta = await fetch(`${environment.apiUrl}/medicamentos/categorias`, {
        cache: 'no-store',
      });
      if (resposta.ok) {
        this.janela?.location.reload();
        return;
      }
      this.apiSemResposta.set(true);
    } catch {
      this.apiSemResposta.set(true);
    } finally {
      this.verificandoApi.set(false);
    }
  }

  private limpar(): void {
    this.comDadosSalvos.clear();
    this.salvoEm.set(null);
    this.apiSemResposta.set(false);
  }
}
