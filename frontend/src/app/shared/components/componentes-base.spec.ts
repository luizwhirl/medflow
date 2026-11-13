import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Button } from './button';
import { Card } from './card';
import { Chip } from './chip';
import { List } from './list';
import { SearchField } from './search-field';
import { StatusBadge } from './status-badge';
import { SyncIndicator } from './sync-indicator';

@Component({
  imports: [Card, List, Chip, Button, SearchField],
  template: `
    <app-card pad="lg">conteúdo</app-card>
    <app-list [vazia]="itens().length === 0" vazio="Nada aqui">
      @for (i of itens(); track i) {
        <li>{{ i }}</li>
      }
    </app-list>
    <app-chip [(selecionado)]="filtro">Diabetes</app-chip>
    <button appButton variante="secundario">Ok</button>
    <app-search-field [(valor)]="termo" (buscar)="buscado = $event" />
  `,
})
class Host {
  readonly itens = signal<string[]>([]);
  filtro = false;
  termo = '';
  buscado: string | null = null;
}

function montar() {
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, host: fixture.componentInstance };
}

describe('componentes base', () => {
  it('Card aplica o padding pedido e projeta o conteúdo', () => {
    const card = montar().el.querySelector('app-card')!;
    expect(card.classList).toContain('p-6');
    expect(card.classList).toContain('bg-surface');
    expect(card.textContent).toContain('conteúdo');
  });

  it('List mostra a mensagem de vazio só quando não há itens', () => {
    const { fixture, el, host } = montar();
    expect(el.querySelector('app-list')!.textContent?.trim()).toBe('Nada aqui');

    host.itens.set(['A', 'B']);
    fixture.detectChanges();
    const itens = Array.from(el.querySelectorAll('app-list li')).map((li) => li.textContent);
    expect(itens).toEqual(['A', 'B']);
  });

  it('Chip alterna a seleção e reflete em aria-pressed', () => {
    const { fixture, el, host } = montar();
    const botao = el.querySelector('app-chip button') as HTMLButtonElement;
    expect(botao.getAttribute('aria-pressed')).toBe('false');

    botao.click();
    fixture.detectChanges();
    expect(host.filtro).toBeTrue();
    expect(botao.getAttribute('aria-pressed')).toBe('true');
  });

  it('Button aplica a variante', () => {
    const botao = montar().el.querySelector('button[appButton]')!;
    expect(botao.classList).toContain('border-outline');
    expect(botao.classList).not.toContain('bg-brand-600');
  });

  it('SearchField atualiza o valor e emite busca no Enter', () => {
    const { el, host } = montar();
    const input = el.querySelector('app-search-field input') as HTMLInputElement;
    input.value = 'losartana';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(host.termo).toBe('losartana');
    expect(host.buscado).toBe('losartana');
  });
});

describe('StatusBadge', () => {
  it('usa o tamanho maior quando pedido', () => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', 'DISPONIVEL');
    fixture.componentRef.setInput('tamanho', 'md');
    fixture.detectChanges();
    const span = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    expect(span.classList).toContain('text-sm');
    expect(span.textContent?.trim()).toBe('Disponível');
  });
});

describe('SyncIndicator', () => {
  function texto(em: string | null): string {
    const fixture = TestBed.createComponent(SyncIndicator);
    fixture.componentRef.setInput('em', em);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.trim();
  }

  it('descreve há quanto tempo sincronizou', () => {
    expect(texto(null)).toBe('Nunca sincronizado');
    expect(texto(new Date(Date.now() - 10 * 60_000).toISOString())).toBe('Sincronizado há 10 min');
    expect(texto(new Date(Date.now() - 3 * 3_600_000).toISOString())).toBe('Sincronizado há 3 h');
  });
});
