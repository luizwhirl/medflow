import { Component, signal } from '@angular/core';
import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';

import {
  AMBIENTE_RECONHECIMENTO_VOZ,
  ResultadoVoz,
} from '../../core/services/reconhecimento-voz.service';
import type {
  AmbienteReconhecimentoVoz,
  DisponibilidadeReconhecimento,
  EventoErroReconhecimento,
  EventoResultadoReconhecimento,
  ReconhecimentoVoz,
  ResultadoReconhecimento,
} from '../../core/types/speech-recognition';
import { CHAVE_AVISO_VOZ, SearchField } from './search-field';

/** SpeechRecognition falsa: os testes disparam os eventos que o navegador dispararia. */
class ReconhecimentoFalso implements ReconhecimentoVoz {
  static instancias: ReconhecimentoFalso[] = [];
  static disponivel: DisponibilidadeReconhecimento | null = null;

  static available = jasmine
    .createSpy('available')
    .and.callFake(async () => ReconhecimentoFalso.disponivel ?? 'unavailable');

  lang = '';
  continuous = false;
  interimResults = false;
  maxAlternatives = 1;
  processLocally?: boolean;
  onstart: (() => void) | null = null;
  onspeechend: (() => void) | null = null;
  onresult: ((evento: EventoResultadoReconhecimento) => void) | null = null;
  onnomatch: (() => void) | null = null;
  onerror: ((evento: EventoErroReconhecimento) => void) | null = null;
  onend: (() => void) | null = null;
  start = jasmine.createSpy('start').and.callFake(() => this.onstart?.());
  stop = jasmine.createSpy('stop');
  abort = jasmine.createSpy('abort');

  constructor() {
    ReconhecimentoFalso.instancias.push(this);
  }

  resultado(final: boolean, ...textos: string[]): void {
    const r: ResultadoReconhecimento = Object.assign(
      textos.map((transcript, i) => ({ transcript, confidence: 1 - i * 0.1 })),
      { isFinal: final },
    );
    this.onresult?.({ resultIndex: 0, results: [r] });
  }
}

@Component({
  imports: [SearchField],
  template: `<app-search-field
    [(valor)]="valor"
    (buscar)="buscas.push($event)"
    (voz)="vozes.push($event)"
  />`,
})
class Hospedeiro {
  readonly valor = signal('');
  readonly buscas: string[] = [];
  readonly vozes: ResultadoVoz[] = [];
}

describe('SearchField · busca por voz', () => {
  let fixture: ComponentFixture<Hospedeiro>;
  let el: HTMLElement;

  function montar(ambiente: AmbienteReconhecimentoVoz | null) {
    TestBed.configureTestingModule({
      providers: [{ provide: AMBIENTE_RECONHECIMENTO_VOZ, useValue: ambiente }],
    });
    fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  }

  const comSuporte = (): AmbienteReconhecimentoVoz => ({
    isSecureContext: true,
    SpeechRecognition: ReconhecimentoFalso,
  });
  const microfone = () => el.querySelector<HTMLButtonElement>('button[aria-pressed]');
  const campo = () => el.querySelector<HTMLInputElement>('input')!;
  const atual = () => ReconhecimentoFalso.instancias[ReconhecimentoFalso.instancias.length - 1];
  const mensagem = () => el.querySelector('[aria-live]')!.textContent!.trim();
  const botao = (texto: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes(texto))!;

  /** Clica no microfone e deixa a verificação do reconhecimento local terminar. */
  function clicarMicrofone() {
    microfone()!.click();
    flushMicrotasks();
    fixture.detectChanges();
  }

  beforeEach(() => {
    ReconhecimentoFalso.instancias = [];
    ReconhecimentoFalso.disponivel = null;
    localStorage.setItem(CHAVE_AVISO_VOZ, 'sim');
  });

  afterEach(() => localStorage.removeItem(CHAVE_AVISO_VOZ));

  it('sem suporte no navegador (Firefox), não mostra o microfone', () => {
    montar({ isSecureContext: true });
    expect(microfone()).toBeNull();
  });

  it('mostra o texto parcial no campo e, no final, preenche e busca com as alternativas', fakeAsync(() => {
    montar(comSuporte());
    expect(microfone()!.getAttribute('aria-label')).toBe('Buscar por voz');

    clicarMicrofone();
    expect(microfone()!.getAttribute('aria-pressed')).toBe('true');
    expect(microfone()!.getAttribute('aria-label')).toBe('Parar de ouvir');
    expect(mensagem()).toBe('Ouvindo…');

    atual().resultado(false, 'losar');
    fixture.detectChanges();
    expect(campo().value).toBe('losar');

    atual().resultado(true, 'losartana', 'losartan');
    atual().onend?.();
    flushMicrotasks();
    fixture.detectChanges();

    expect(fixture.componentInstance.valor()).toBe('losartana');
    expect(campo().value).toBe('losartana');
    expect(fixture.componentInstance.buscas).toEqual(['losartana']);
    expect(fixture.componentInstance.vozes).toEqual([
      { texto: 'losartana', alternativas: ['losartan'] },
    ]);
    expect(mensagem()).toBe('Buscando por losartana');
    expect(document.activeElement).toBe(campo());
  }));

  it('o segundo clique enquanto ouve encerra a escuta', fakeAsync(() => {
    montar(comSuporte());
    clicarMicrofone();

    microfone()!.click();
    expect(atual().stop).toHaveBeenCalled();
    atual().onend?.();
    flushMicrotasks();
    fixture.detectChanges();
    expect(microfone()!.getAttribute('aria-pressed')).toBe('false');
    expect(fixture.componentInstance.buscas).toEqual([]);
  }));

  const casos: [string, string][] = [
    ['not-allowed', 'Permita o uso do microfone'],
    ['service-not-allowed', 'Permita o uso do microfone'],
    ['no-speech', 'Não ouvimos nada.'],
    ['audio-capture', 'Não encontramos um microfone neste aparelho.'],
    ['network', 'A busca por voz precisa de internet.'],
  ];
  for (const [codigo, texto] of casos) {
    it(`erro ${codigo} mostra "${texto}" e some ao digitar`, fakeAsync(() => {
      montar(comSuporte());
      clicarMicrofone();
      atual().onerror?.({ error: codigo });
      flushMicrotasks();
      fixture.detectChanges();
      expect(mensagem()).toContain(texto);

      campo().value = 'met';
      campo().dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(mensagem()).toBe('');
    }));
  }

  it('sem resultado (nomatch) diz que não ouviu nada; cancelar (aborted) não mostra nada', fakeAsync(() => {
    montar(comSuporte());
    clicarMicrofone();
    atual().onnomatch?.();
    flushMicrotasks();
    fixture.detectChanges();
    expect(mensagem()).toContain('Não ouvimos nada.');

    clicarMicrofone();
    expect(mensagem()).toBe('Ouvindo…');
    atual().onerror?.({ error: 'aborted' });
    flushMicrotasks();
    fixture.detectChanges();
    expect(mensagem()).toBe('');
  }));

  it('idioma não suportado esconde o microfone', fakeAsync(() => {
    montar(comSuporte());
    clicarMicrofone();
    atual().onerror?.({ error: 'language-not-supported' });
    flushMicrotasks();
    fixture.detectChanges();
    expect(microfone()).toBeNull();
    expect(mensagem()).toBe('');
  }));

  describe('aviso de privacidade', () => {
    beforeEach(() => localStorage.removeItem(CHAVE_AVISO_VOZ));

    it('aparece no primeiro uso e "Agora não" não liga o microfone', fakeAsync(() => {
      montar(comSuporte());
      clicarMicrofone();
      expect(el.querySelector('[data-aviso-voz]')!.textContent).toContain('não grava o áudio');
      expect(ReconhecimentoFalso.instancias.length).toBe(0);

      botao('Agora não').click();
      fixture.detectChanges();
      expect(el.querySelector('[data-aviso-voz]')).toBeNull();
      expect(ReconhecimentoFalso.instancias.length).toBe(0);
      expect(localStorage.getItem(CHAVE_AVISO_VOZ)).toBeNull();
      expect(document.activeElement).toBe(microfone());
    }));

    it('"Continuar" liga o microfone e não pergunta de novo', fakeAsync(() => {
      montar(comSuporte());
      clicarMicrofone();
      botao('Continuar').click();
      fixture.detectChanges();
      expect(atual().start).toHaveBeenCalled();
      expect(atual().processLocally).toBeUndefined();
      expect(localStorage.getItem(CHAVE_AVISO_VOZ)).toBe('sim');

      microfone()!.click(); // para
      atual().onend?.();
      flushMicrotasks();
      fixture.detectChanges();
      clicarMicrofone();
      expect(el.querySelector('[data-aviso-voz]')).toBeNull();
      expect(ReconhecimentoFalso.instancias.length).toBe(2);
    }));

    it('com reconhecimento no aparelho, usa o local e pula o aviso', fakeAsync(() => {
      ReconhecimentoFalso.disponivel = 'available';
      montar(comSuporte());
      clicarMicrofone();
      expect(ReconhecimentoFalso.available).toHaveBeenCalledWith({
        langs: ['pt-BR'],
        processLocally: true,
      });
      expect(el.querySelector('[data-aviso-voz]')).toBeNull();
      expect(atual().processLocally).toBeTrue();
    }));

    it('pacote ainda não baixado (downloadable) não conta como local', fakeAsync(() => {
      ReconhecimentoFalso.disponivel = 'downloadable';
      montar(comSuporte());
      clicarMicrofone();
      expect(el.querySelector('[data-aviso-voz]')).not.toBeNull();
    }));
  });

  it('clique duplo enquanto confere o reconhecimento local abre uma escuta só', fakeAsync(() => {
    montar(comSuporte());
    microfone()!.click();
    microfone()!.click();
    flushMicrotasks();
    fixture.detectChanges();
    expect(ReconhecimentoFalso.instancias.length).toBe(1);
    expect(microfone()!.getAttribute('aria-pressed')).toBe('true');
  }));

  it('se o reconhecimento no aparelho não aceitar pt-BR, volta para o do navegador', fakeAsync(() => {
    ReconhecimentoFalso.disponivel = 'available';
    montar(comSuporte());
    clicarMicrofone();
    expect(atual().processLocally).toBeTrue();

    atual().onerror?.({ error: 'language-not-supported' });
    atual().onend?.();
    flushMicrotasks();
    fixture.detectChanges();
    // O aviso já foi aceito: tenta de novo sem o modo local, e o microfone continua na tela.
    expect(ReconhecimentoFalso.instancias.length).toBe(2);
    expect(atual().processLocally).toBeUndefined();
    expect(microfone()).not.toBeNull();
  }));

  it('o aviso de privacidade recebe o foco e fecha com Esc', fakeAsync(() => {
    localStorage.removeItem(CHAVE_AVISO_VOZ);
    montar(comSuporte());
    clicarMicrofone();
    expect(document.activeElement).toBe(botao('Continuar'));

    el.querySelector('[data-aviso-voz]')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape' }),
    );
    fixture.detectChanges();
    expect(el.querySelector('[data-aviso-voz]')).toBeNull();
    expect(document.activeElement).toBe(microfone());
  }));

  it('o rótulo aponta para o campo e o botão fica fora do rótulo', () => {
    montar(comSuporte());
    const rotulo = el.querySelector('label')!;
    expect(rotulo.getAttribute('for')).toBe(campo().id);
    expect(rotulo.querySelector('button')).toBeNull();
  });
});
