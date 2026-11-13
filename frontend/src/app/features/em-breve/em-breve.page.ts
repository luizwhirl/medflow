import { Component, input } from '@angular/core';

/** Placeholder para abas cuja API ainda não existe (Minha lista, Perfil). */
@Component({
  selector: 'app-em-breve-page',
  template: `
    <section class="surface-card p-6">
      <h1 class="page-title">{{ titulo() }}</h1>
      <p class="mt-2 body-muted">Esta área ainda está em construção.</p>
    </section>
  `,
})
export class EmBrevePage {
  /** Vem de `data.titulo` da rota (withComponentInputBinding). */
  readonly titulo = input('Em breve');
}
