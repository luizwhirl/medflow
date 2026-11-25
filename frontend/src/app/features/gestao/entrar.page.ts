import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessaoService } from '../../core/auth/sessao.service';
import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';

/** Entrada da equipe da farmácia (e-mail e senha da conta de gestor). */
@Component({
  selector: 'app-entrar-page',
  imports: [Button, Card, RouterLink],
  template: `
    <main class="grid min-h-dvh place-items-center bg-canvas px-4 py-10">
      <app-card class="flex w-full max-w-sm flex-col gap-5">
        <div class="flex flex-col gap-1">
          <p class="text-sm font-semibold text-brand-600">UBS Conecta · Gestão da farmácia</p>
          <h1 class="page-title">Entrar</h1>
          <p class="body-muted">Use a conta de gestor da sua unidade.</p>
        </div>

        <form class="flex flex-col gap-4" (submit)="entrar($event)">
          <label class="flex flex-col gap-1">
            <span class="text-sm font-semibold text-content">E-mail</span>
            <input
              class="control w-full"
              type="email"
              name="email"
              autocomplete="username"
              required
              [value]="email()"
              (input)="email.set($any($event.target).value)"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-sm font-semibold text-content">Senha</span>
            <input
              class="control w-full"
              type="password"
              name="senha"
              autocomplete="current-password"
              required
              [value]="senha()"
              (input)="senha.set($any($event.target).value)"
            />
          </label>

          @if (erro()) {
            <p class="feedback-error" role="alert">{{ erro() }}</p>
          }

          <button appButton type="submit" [disabled]="enviando()">
            {{ enviando() ? 'Entrando…' : 'Entrar' }}
          </button>
        </form>

        <a routerLink="/" class="text-center text-sm text-brand-600 hover:underline"
          >Voltar para o app</a
        >
      </app-card>
    </main>
  `,
})
export class EntrarPage {
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);

  /** Para onde voltar depois de entrar (vem do guard ou do interceptor). */
  readonly volta = input<string>();

  protected readonly email = signal('');
  protected readonly senha = signal('');
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected entrar(evento: Event): void {
    evento.preventDefault();
    if (this.enviando()) return;
    this.erro.set(null);
    this.enviando.set(true);
    this.sessao.entrar(this.email().trim(), this.senha()).subscribe({
      next: (r) => {
        this.enviando.set(false);
        if (r.papel !== 'GESTOR') {
          this.sessao.sair();
          this.erro.set(
            'Esta conta não é de gestor. A área da gestão é só para a equipe da farmácia.',
          );
          return;
        }
        void this.router.navigateByUrl(destinoSeguro(this.volta()));
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.erro.set(
          e instanceof HttpErrorResponse && e.status === 401
            ? 'E-mail ou senha incorretos.'
            : 'Não foi possível entrar agora. Confira a conexão e tente de novo.',
        );
      },
    });
  }
}

/** Só volta para rotas da própria gestão (evita redirecionar para fora do app). */
function destinoSeguro(volta: string | undefined): string {
  return volta && volta.startsWith('/gestao') && !volta.startsWith('/gestao/entrar')
    ? volta
    : '/gestao';
}
