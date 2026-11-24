import { Component, computed, inject } from '@angular/core';

import { ConexaoService } from '../../core/offline/conexao.service';

/**
 * Banner de conexão, no topo de todas as telas (nas duas cascas). Aparece sem internet ou quando
 * a tela está mostrando dados salvos, sempre com o horário desses dados.
 * Estilo do "Banner em falta" do Figma, no tom âmbar de alerta.
 */
@Component({
  selector: 'app-banner-offline',
  host: { class: 'block' },
  template: `
    <div role="status" aria-live="polite">
      @if (mensagem(); as m) {
        <div
          class="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-status-reservado/20 bg-status-reservado-subtle px-4 py-3 text-status-alerta sm:px-8 xl:px-14"
          data-banner="conexao"
        >
          <svg
            class="size-5 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path [attr.d]="m.icone" />
          </svg>
          <p class="min-w-0 flex-1 text-sm">
            <b>{{ m.titulo }}</b> {{ m.texto }}
          </p>
          @if (conexao.online()) {
            <button
              type="button"
              class="text-sm font-semibold underline"
              (click)="conexao.tentarDeNovo()"
            >
              Atualizar
            </button>
          }
        </div>
      }
    </div>
  `,
})
export class BannerOffline {
  protected readonly conexao = inject(ConexaoService);

  protected readonly mensagem = computed(() => {
    const online = this.conexao.online();
    const em = this.conexao.dadosSalvosEm();
    if (online && em === null) return null;
    const dados = em === null ? null : `Mostrando dados salvos ${quando(em)}.`;
    if (!online) {
      return {
        icone: ICONE_SEM_REDE,
        titulo: 'Sem internet.',
        texto: dados ?? 'Você vê o que já foi carregado; o resto aparece quando a conexão voltar.',
      };
    }
    return {
      icone: ICONE_RELOGIO,
      titulo: 'Não foi possível atualizar.',
      texto: dados!,
    };
  });
}

const ICONE_SEM_REDE =
  'M2 8.8a15 15 0 0 1 4.2-2.7M22 8.8a15 15 0 0 0-11.3-3.7M5 12.9a10 10 0 0 1 5.2-2.8M19 12.9a10 10 0 0 0-1.9-1.4M8.5 16.4a5 5 0 0 1 7 0M12 20h.01M3 3l18 18';
const ICONE_RELOGIO = 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2';

/** "hoje às 14:05", "ontem às 09:30" ou "em 22/11 às 18:00". */
export function quando(em: number, agora = Date.now()): string {
  const data = new Date(em);
  const hora = `${dois(data.getHours())}:${dois(data.getMinutes())}`;
  const dias = diasEntre(data, new Date(agora));
  if (dias === 0) return `hoje às ${hora}`;
  if (dias === 1) return `ontem às ${hora}`;
  return `em ${dois(data.getDate())}/${dois(data.getMonth() + 1)} às ${hora}`;
}

function diasEntre(a: Date, b: Date): number {
  const meiaNoite = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Math.round((meiaNoite(b) - meiaNoite(a)) / 86_400_000);
}

function dois(n: number): string {
  return String(n).padStart(2, '0');
}
