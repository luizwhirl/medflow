import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { defer, Observable, of, Subject, throwError } from 'rxjs';

import { PainelGestao } from '../../core/models/gestao.model';
import { GestaoService, painelDemonstracao } from '../../core/services/gestao.service';
import { PainelGestaoPage } from './painel-gestao.page';

describe('PainelGestaoPage', () => {
  // Segunda, 19/10/2026: a próxima remessa (+3 dias) cai na quinta, 22/10, como no Figma.
  const painel = painelDemonstracao(new Date(2026, 9, 19));

  function montar(
    painel$: Observable<PainelGestao> = of(painel),
  ): ComponentFixture<PainelGestaoPage> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: GestaoService, useValue: { painel: () => painel$ } },
      ],
    });
    const fixture = TestBed.createComponent(PainelGestaoPage);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<PainelGestaoPage>) => f.nativeElement as HTMLElement;
  const texto = (e: Element | null) => e?.textContent?.replace(/\s+/g, ' ').trim();
  const linhas = (f: ComponentFixture<PainelGestaoPage>) =>
    Array.from(el(f).querySelectorAll('tbody tr th')).map(texto);
  const chip = (f: ComponentFixture<PainelGestaoPage>, rotulo: string) =>
    Array.from(el(f).querySelectorAll<HTMLButtonElement>('app-chip button')).find(
      (b) => texto(b) === rotulo,
    )!;

  it('mostra os quatro indicadores do Figma', () => {
    const kpis = Array.from(
      el(montar()).querySelectorAll('section[aria-label="Indicadores"] app-card'),
    ).map((card) => Array.from(card.children).map(texto).join(' '));
    expect(kpis).toEqual([
      'Itens em falta 6 2 com remessa a caminho',
      'Abaixo do mínimo 14 Repor nos próximos 7 dias',
      'Remessas a caminho 2 Próxima: qui, 22/10',
      'Pacientes aguardando aviso 87 Em 9 medicamentos',
    ]);
  });

  it('lista os itens que pedem atenção com a situação e o dia da remessa', () => {
    const f = montar();
    expect(linhas(f).length).toBe(5);
    const situacoes = Array.from(el(f).querySelectorAll('tbody td:last-child span')).map(texto);
    expect(situacoes).toEqual([
      'Em falta · remessa qui',
      'Em falta · remessa sáb',
      'Abaixo do mínimo',
      'Abaixo do mínimo',
      'Pedido enviado',
    ]);
  });

  it('filtra por situação e volta para "Todos" ao desmarcar', () => {
    const f = montar();
    chip(f, 'Em falta').click();
    f.detectChanges();
    expect(linhas(f)).toEqual(['Metformina 850 mg', 'Insulina humana NPH 100 UI/mL']);

    chip(f, 'Abaixo do mínimo').click();
    f.detectChanges();
    expect(linhas(f)).toEqual(['Sinvastatina 20 mg', 'Omeprazol 20 mg']);
    expect(chip(f, 'Em falta').getAttribute('aria-pressed')).toBe('false');

    chip(f, 'Abaixo do mínimo').click();
    f.detectChanges();
    expect(linhas(f).length).toBe(5);
    expect(chip(f, 'Todos').getAttribute('aria-pressed')).toBe('true');
  });

  it('"Todos" continua marcado ao ser clicado de novo', () => {
    const f = montar();
    chip(f, 'Todos').click();
    f.detectChanges();
    expect(chip(f, 'Todos').getAttribute('aria-pressed')).toBe('true');
    expect(linhas(f).length).toBe(5);
  });

  it('"Exportar" baixa só os itens do filtro ativo, com BOM para o Excel', async () => {
    let blob: Blob | undefined;
    spyOn(URL, 'createObjectURL').and.callFake((b: Blob | MediaSource) => {
      blob = b as Blob;
      return 'blob:teste';
    });
    spyOn(URL, 'revokeObjectURL');
    const clique = spyOn(HTMLAnchorElement.prototype, 'click');

    const f = montar();
    chip(f, 'Em falta').click();
    f.detectChanges();
    Array.from(el(f).querySelectorAll<HTMLButtonElement>('header button'))
      .find((b) => texto(b) === 'Exportar')!
      .click();

    expect(clique).toHaveBeenCalled();
    const bytes = new Uint8Array(await blob!.arrayBuffer());
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
    const linhasCsv = new TextDecoder().decode(bytes.slice(3)).split('\r\n');
    expect(linhasCsv.length).toBe(3);
    expect(linhasCsv[1]).toContain('"Metformina 850 mg"');
    expect(linhasCsv[2]).toContain('"Insulina humana NPH 100 UI/mL"');
  });

  it('barras dos mais procurados são proporcionais ao maior número de buscas', () => {
    const barras = Array.from(
      el(montar()).querySelectorAll<HTMLElement>('ul li [aria-hidden] > div'),
    ).map((b) => Math.round(parseFloat(b.style.width)));
    expect(barras).toEqual([100, 74, 48, 45, 21]);
  });

  it('mostra o esqueleto enquanto os indicadores carregam', () => {
    const f = montar(new Subject<PainelGestao>());
    expect(el(f).querySelector('app-esqueleto[role="status"]')?.getAttribute('aria-label')).toBe(
      'Carregando os indicadores da farmácia',
    );
    expect(el(f).querySelector('section[aria-label="Indicadores"]')).toBeNull();
  });

  it('mostra erro quando os indicadores não carregam e tenta de novo', () => {
    let tentativas = 0;
    const f = montar(
      defer(() => (++tentativas === 1 ? throwError(() => new Error('falhou')) : of(painel))),
    );
    expect(texto(el(f).querySelector('[role="alert"]'))).toContain(
      'Não foi possível carregar os indicadores da farmácia',
    );

    Array.from(el(f).querySelectorAll<HTMLButtonElement>('[role="alert"] button'))
      .find((b) => texto(b) === 'Tentar de novo')!
      .click();
    f.detectChanges();
    expect(tentativas).toBe(2);
    expect(el(f).querySelector('[role="alert"]')).toBeNull();
    expect(linhas(f).length).toBe(5);
  });

  it('sem itens no filtro, mostra o estado vazio', () => {
    const f = montar(of({ ...painel, itensAtencao: [], maisProcurados: [] }));
    expect(texto(el(f).querySelector('tbody'))).toBe('Nenhum item pede atenção agora');
    expect(texto(el(f).querySelector('ul'))).toContain('Nenhuma busca registrada');
  });
});
