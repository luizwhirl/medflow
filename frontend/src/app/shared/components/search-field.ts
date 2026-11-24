import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';

import {
  ErroVoz,
  ReconhecimentoVozService,
  ResultadoVoz,
} from '../../core/services/reconhecimento-voz.service';

/** Escolha do aviso de privacidade da busca por voz, guardada no navegador. */
export const CHAVE_AVISO_VOZ = 'medflow.voz.avisoAceito';

/** Mensagem de cada erro do reconhecimento; `idioma-nao-suportado` esconde o botão. */
const MENSAGENS: Record<Exclude<ErroVoz, 'idioma-nao-suportado'>, string> = {
  'sem-permissao':
    'Permita o uso do microfone para buscar por voz: toque no cadeado ao lado do endereço e libere o microfone.',
  'sem-fala': 'Não ouvimos nada. Toque no microfone e diga o nome do remédio.',
  'sem-microfone': 'Não encontramos um microfone neste aparelho.',
  'sem-rede': 'A busca por voz precisa de internet. Digite o nome do remédio.',
  outro: 'Não foi possível usar a busca por voz agora. Digite o nome do remédio.',
};

const ICONE_MICROFONE =
  'M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3';

/**
 * Campo de busca com ícone de lupa. `valor` é two-way; Enter emite `buscar`.
 * Com `rotuloVisivel`, o rótulo aparece acima do campo (como no Figma).
 *
 * Onde o navegador reconhece voz, tem um botão de microfone (PDS-52): o texto parcial aparece no
 * campo e o resultado final preenche o campo e emite `buscar` (e `voz`, com as alternativas).
 * Antes da primeira escuta mostra o aviso de privacidade, a menos que o reconhecimento seja feito
 * no próprio aparelho (PDS-53).
 */
@Component({
  selector: 'app-search-field',
  template: `
    <label class="block">
      <span
        [class]="rotuloVisivel() ? 'mb-2 block text-sm font-semibold text-content' : 'sr-only'"
        >{{ rotulo() }}</span
      >
      <span class="relative block">
        <svg
          class="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-content-muted"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5" />
        </svg>
        <input
          #campo
          type="search"
          class="control h-12 w-full pl-12"
          [class.pr-14]="microfone()"
          [placeholder]="placeholder()"
          [value]="exibido()"
          (input)="digitar($any($event.target).value)"
          (keydown.enter)="$event.preventDefault(); buscar.emit(valor())"
        />
        @if (microfone()) {
          <button
            #botaoVoz
            type="button"
            class="absolute right-0.5 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-control text-content-muted hover:text-brand-600"
            [class]="ouvindo() ? 'bg-brand-50 text-brand-600 motion-safe:animate-pulse' : ''"
            [attr.aria-label]="ouvindo() ? 'Parar de ouvir' : 'Buscar por voz'"
            [attr.aria-pressed]="ouvindo()"
            (click)="alternarVoz()"
          >
            <svg
              class="size-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <path [attr.d]="iconeMicrofone" />
            </svg>
          </button>
        }
      </span>
    </label>

    @if (avisoAberto()) {
      <div
        class="mt-2 rounded-card border border-outline bg-surface p-4 text-sm"
        role="dialog"
        aria-labelledby="aviso-voz-titulo"
        aria-describedby="aviso-voz-texto"
        data-aviso-voz
      >
        <p id="aviso-voz-titulo" class="font-semibold text-content">Antes de buscar por voz</p>
        <p id="aviso-voz-texto" class="mt-1 text-content-muted">
          O áudio do que você falar vai para o serviço de reconhecimento de voz do seu navegador
          (Google no Chrome, Microsoft no Edge), que devolve o texto. O UBS Conecta não grava o
          áudio, e o texto serve só para buscar o remédio.
        </p>
        <div class="mt-3 flex gap-2">
          <button
            type="button"
            class="rounded-control bg-brand-600 px-4 py-2 font-semibold text-white"
            (click)="continuar()"
          >
            Continuar
          </button>
          <button
            type="button"
            class="rounded-control px-4 py-2 font-semibold text-brand-600"
            (click)="agoraNao()"
          >
            Agora não
          </button>
        </div>
      </div>
    }

    <p
      class="mt-1 text-sm empty:hidden"
      [class]="mensagem() ? 'text-status-falta' : ''"
      aria-live="polite"
    >
      @if (mensagem(); as m) {
        {{ m }}
      } @else if (anuncio(); as a) {
        <span class="sr-only">{{ a }}</span>
      }
    </p>
  `,
})
export class SearchField {
  readonly valor = model('');
  readonly placeholder = input('Buscar remédio');
  readonly rotulo = input('Buscar medicamento');
  readonly rotuloVisivel = input(false);
  readonly buscar = output<string>();
  /** Resultado da busca por voz, com as alternativas do reconhecimento (emitido junto com `buscar`). */
  readonly voz = output<ResultadoVoz>();

  private readonly reconhecimento = inject(ReconhecimentoVozService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');
  private readonly botaoVoz = viewChild<ElementRef<HTMLButtonElement>>('botaoVoz');

  protected readonly iconeMicrofone = ICONE_MICROFONE;
  /** Some de vez se o navegador disser que não reconhece pt-BR. */
  private readonly semIdioma = signal(false);
  protected readonly microfone = computed(() => this.reconhecimento.suportado && !this.semIdioma());
  /** Esta escuta é deste campo (a Início e a Buscar podem ter cada uma o seu). */
  private readonly escutando = signal(false);
  protected readonly ouvindo = computed(
    () => this.escutando() && this.reconhecimento.estado() !== 'inativo',
  );
  protected readonly avisoAberto = signal(false);
  protected readonly mensagem = signal<string | null>(null);
  protected readonly anuncio = signal('');

  /** Enquanto ouve, o campo mostra o que já foi reconhecido. */
  protected readonly exibido = computed(() =>
    this.escutando() && this.reconhecimento.parcial()
      ? this.reconhecimento.parcial()
      : this.valor(),
  );

  protected digitar(texto: string): void {
    this.mensagem.set(null);
    this.valor.set(texto);
  }

  protected async alternarVoz(): Promise<void> {
    if (this.ouvindo()) {
      this.reconhecimento.parar();
      return;
    }
    this.mensagem.set(null);
    const local = await this.reconhecimento.reconhecimentoLocalDisponivel();
    if (!local && !avisoAceito()) {
      this.avisoAberto.set(true);
      return;
    }
    this.ouvir(local);
  }

  protected continuar(): void {
    try {
      localStorage.setItem(CHAVE_AVISO_VOZ, 'sim');
    } catch {
      // Sem armazenamento (aba privada): o aviso volta na próxima visita.
    }
    this.avisoAberto.set(false);
    this.ouvir(false);
  }

  protected agoraNao(): void {
    this.avisoAberto.set(false);
    this.botaoVoz()?.nativeElement.focus();
  }

  private async ouvir(local: boolean): Promise<void> {
    this.escutando.set(true);
    this.anuncio.set('Ouvindo…');
    const resultado = await this.reconhecimento.ouvir(this.destroyRef, { local });
    this.escutando.set(false);
    if (this.destroyRef.destroyed) return;
    this.campo().nativeElement.focus();

    if (resultado) {
      this.valor.set(resultado.texto);
      this.anuncio.set(`Buscando por ${resultado.texto}`);
      this.voz.emit(resultado);
      this.buscar.emit(resultado.texto);
      return;
    }
    this.anuncio.set('');
    const erro = this.reconhecimento.erro();
    if (erro === 'idioma-nao-suportado') this.semIdioma.set(true);
    else if (erro) this.mensagem.set(MENSAGENS[erro]);
  }
}

function avisoAceito(): boolean {
  try {
    return localStorage.getItem(CHAVE_AVISO_VOZ) === 'sim';
  } catch {
    return false;
  }
}
