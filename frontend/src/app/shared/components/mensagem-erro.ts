import { Component, computed, inject, input, output } from '@angular/core';

import { ConexaoService } from '../../core/offline/conexao.service';

const ICONE_ALERTA = 'M12 3 2 20h20zM12 10v4M12 17h.01';
const ICONE_SEM_REDE =
  'M2 8.8a15 15 0 0 1 4.2-2.7M22 8.8a15 15 0 0 0-11.3-3.7M5 12.9a10 10 0 0 1 5.2-2.8M19 12.9a10 10 0 0 0-1.9-1.4M8.5 16.4a5 5 0 0 1 7 0M12 20h.01M3 3l18 18';

/**
 * Erro ao carregar uma tela (ou parte dela), com "Tentar de novo". Sem internet, explica que não há
 * dados salvos desta tela no aparelho, em vez da mensagem genérica.
 */
@Component({
  selector: 'app-mensagem-erro',
  host: { class: 'block' },
  template: `
    <div
      class="flex items-start gap-3 rounded-card bg-status-falta-subtle p-5 text-status-falta"
      role="alert"
    >
      <svg
        class="mt-0.5 size-5 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path [attr.d]="online() ? iconeAlerta : iconeSemRede" />
      </svg>
      <div class="min-w-0 flex-1">
        <p class="font-bold">{{ online() ? titulo() : 'Sem internet' }}</p>
        <p class="mt-1 text-sm">{{ explicacao() }}</p>
        @if (comAcao()) {
          <button
            type="button"
            class="mt-3 rounded-control border border-current px-4 py-2 text-sm font-semibold hover:bg-white/60"
            (click)="tentarDeNovo.emit()"
          >
            Tentar de novo
          </button>
        }
      </div>
    </div>
  `,
})
export class MensagemErro {
  private readonly conexao = inject(ConexaoService);

  readonly titulo = input('Não foi possível carregar');
  readonly texto = input('Verifique a conexão e tente de novo.');
  /** Mostra o botão "Tentar de novo" (que emite `tentarDeNovo`). */
  readonly comAcao = input(true);
  readonly tentarDeNovo = output<void>();

  protected readonly online = this.conexao.online;
  protected readonly iconeAlerta = ICONE_ALERTA;
  protected readonly iconeSemRede = ICONE_SEM_REDE;

  protected readonly explicacao = computed(() =>
    this.online()
      ? this.texto()
      : 'Estes dados ainda não foram salvos neste aparelho. Conecte-se e tente de novo.',
  );
}
