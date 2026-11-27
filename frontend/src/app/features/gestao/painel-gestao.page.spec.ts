import {
  ComponentFixture,
  discardPeriodicTasks,
  fakeAsync,
  TestBed,
  tick,
} from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import {
  IndicadoresResponse,
  ItemAtencaoResponse,
  LocalizacaoUbs,
  PainelGestao,
  SituacaoItem,
} from '../../core/models/gestao.model';
import { GestaoService, montarPainel } from '../../core/services/gestao.service';
import { ATUALIZAR_A_CADA_MS, PainelGestaoPage } from './painel-gestao.page';

/** Respostas no formato da API (`/api/gestao/indicadores`, `/itens-atencao` e `/demanda-reprimida`). */
const UBS = { id: 1, nome: 'US Denisson Menezes', cnes: '2003317' };

function indicadores(extra: Partial<IndicadoresResponse> = {}): IndicadoresResponse {
  return {
    ubs: UBS,
    restrito: true,
    ultimaSincronizacao: new Date(Date.now() - 4 * 60_000).toISOString(),
    itensEmFalta: 6,
    emFaltaComRemessa: 2,
    abaixoDoMinimo: 14,
    remessasACaminho: 2,
    remessasAtrasadas: 0,
    proximaRemessa: '2026-10-22',
    pacientesAguardando: 87,
    medicamentosAguardados: 9,
    lotesVencendo: 3,
    ubsComSincronizacaoAtrasada: 0,
    ...extra,
  };
}

function item(
  id: number,
  nome: string,
  estoque: number,
  minimo: number,
  situacao: SituacaoItem,
  remessaPrevista: string | null = null,
  ubs = UBS,
): ItemAtencaoResponse {
  return {
    ubs: { id: ubs.id, nome: ubs.nome },
    medicamento: { id, nome },
    estoque,
    minimo,
    aguardandoAviso: 3,
    situacao,
    remessaPrevista,
  };
}

const ITENS = [
  item(1, 'Metformina 850 mg', 0, 400, 'EM_FALTA', '2026-10-22'),
  item(7, 'Insulina humana NPH 100 UI/mL', 0, 60, 'EM_FALTA', '2026-10-24'),
  item(4, 'Sinvastatina 20 mg', 85, 300, 'ABAIXO_MINIMO'),
  item(5, 'Omeprazol 20 mg', 140, 250, 'ABAIXO_MINIMO'),
  item(3, 'Losartana 50 mg', 20, 200, 'PEDIDO_ENVIADO'),
];

const MAIS_PROCURADOS = [
  { medicamento: { id: 1, nome: 'Metformina 850 mg' }, buscas: 312 },
  { medicamento: { id: 2, nome: 'Losartana 50 mg' }, buscas: 231 },
  { medicamento: { id: 3, nome: 'Dipirona 500 mg' }, buscas: 150 },
];

const painelDaUbs = montarPainel(indicadores(), ITENS, { maisProcurados: MAIS_PROCURADOS });

const LOCALIZACAO: LocalizacaoUbs = {
  ubsId: 1,
  nome: UBS.nome,
  endereco: 'Conj Denisson Menezes, S/N',
  bairro: 'Tabuleiro',
  latitude: -9.5441,
  longitude: -35.7284,
  origem: 'FONTE',
  aproximada: false,
};

describe('PainelGestaoPage', () => {
  let chamadas: number;

  function montar(...respostas: Observable<PainelGestao>[]): ComponentFixture<PainelGestaoPage> {
    chamadas = 0;
    const fila = respostas.length ? respostas : [of(painelDaUbs)];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: GestaoService,
          useValue: {
            painel: () => fila[Math.min(chamadas++, fila.length - 1)],
            localizacao: () => of(LOCALIZACAO),
            corrigirLocalizacao: () => of(LOCALIZACAO),
          },
        },
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
  const kpis = (f: ComponentFixture<PainelGestaoPage>) =>
    Array.from(el(f).querySelectorAll('section[aria-label="Indicadores"] app-card')).map((card) =>
      Array.from(card.children).map(texto).join(' '),
    );

  it('mostra os quatro indicadores com os números da API', () => {
    expect(kpis(montar())).toEqual([
      'Itens em falta 6 2 com remessa a caminho',
      'Abaixo do mínimo 14 Saldo até o mínimo do remédio',
      'Remessas a caminho 2 Próxima: qui, 22/10',
      'Pacientes aguardando aviso 87 Em 9 medicamentos',
    ]);
  });

  it('cabeçalho mostra a UBS do gestor ou a rede toda', () => {
    expect(texto(el(montar()).querySelector('header p'))).toBe(
      'US Denisson Menezes · CNES 2003317',
    );

    TestBed.resetTestingModule();
    const rede = montarPainel(indicadores({ ubs: null, restrito: false }), [], {
      maisProcurados: [],
    });
    expect(texto(el(montar(of(rede))).querySelector('header p'))).toBe(
      'Rede municipal · todas as UBS',
    );
  });

  it('avisa as remessas atrasadas', () => {
    const atrasada = montarPainel(indicadores({ remessasAtrasadas: 1 }), ITENS, {
      maisProcurados: [],
    });
    expect(kpis(montar(of(atrasada)))[2]).toBe(
      'Remessas a caminho 2 Próxima: qui, 22/10 · 1 atrasada(s)',
    );
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

  it('na rede toda, o mesmo remédio aparece uma vez por UBS, com o nome dela', () => {
    const outra = { id: 2, nome: 'US Frei Damião', cnes: '2005883' };
    const rede = montarPainel(
      indicadores({ ubs: null, restrito: false }),
      [
        item(1, 'Metformina 850 mg', 0, 400, 'EM_FALTA'),
        item(1, 'Metformina 850 mg', 0, 400, 'EM_FALTA', null, outra),
      ],
      { maisProcurados: [] },
    );
    expect(linhas(montar(of(rede)))).toEqual([
      'Metformina 850 mg US Denisson Menezes',
      'Metformina 850 mg US Frei Damião',
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
    expect(linhasCsv[0]).toContain('"UBS"');
    expect(linhasCsv[1]).toContain('"Metformina 850 mg"');
    expect(linhasCsv[2]).toContain('"Insulina humana NPH 100 UI/mL"');
  });

  it('barras dos mais procurados são proporcionais ao maior número de buscas', () => {
    const barras = Array.from(
      el(montar()).querySelectorAll<HTMLElement>('ul li [aria-hidden] > div'),
    ).map((b) => Math.round(parseFloat(b.style.width)));
    expect(barras).toEqual([100, 74, 48]);
  });

  it('sem buscas na semana, diz isso em vez de uma lista vazia', () => {
    const vazio = montarPainel(indicadores(), ITENS, { maisProcurados: [] });
    expect(el(montar(of(vazio))).textContent).toContain('Nenhuma busca registrada');
  });

  it('mostra o cartão de localização só quando o painel é de uma UBS', () => {
    expect(el(montar()).querySelector('app-localizacao-ubs')).not.toBeNull();

    TestBed.resetTestingModule();
    const rede = montarPainel(indicadores({ ubs: null, restrito: false }), [], {
      maisProcurados: [],
    });
    expect(el(montar(of(rede))).querySelector('app-localizacao-ubs')).toBeNull();
  });

  it('mostra erro quando os indicadores não carregam', () => {
    const f = montar(throwError(() => new Error('falhou')));
    expect(texto(el(f).querySelector('[role="alert"]'))).toContain('Não foi possível carregar');
  });

  it('atualiza sozinho a cada 5 minutos e mantém os números se uma atualização falhar', fakeAsync(() => {
    const depois = montarPainel(indicadores({ itensEmFalta: 9 }), ITENS, { maisProcurados: [] });
    const f = montar(
      of(painelDaUbs),
      throwError(() => new Error('falhou')),
      of(depois),
    );
    expect(kpis(f)[0]).toContain('Itens em falta 6');

    tick(ATUALIZAR_A_CADA_MS);
    f.detectChanges();
    expect(kpis(f)[0]).toContain('Itens em falta 6');
    expect(el(f).querySelector('[role="alert"]')).toBeNull();

    tick(ATUALIZAR_A_CADA_MS);
    f.detectChanges();
    expect(kpis(f)[0]).toContain('Itens em falta 9');
    expect(chamadas).toBe(3);
    discardPeriodicTasks();
  }));
});
