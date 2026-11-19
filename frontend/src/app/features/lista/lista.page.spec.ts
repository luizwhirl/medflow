import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ItemListaDesejo } from '../../core/models/lista-desejos.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { StatusEstoque } from '../../core/models/status-estoque';
import { Ubs } from '../../core/models/ubs.model';
import { ListaPage } from './lista.page';

const ubs = (id: number, nome: string) =>
  ({ id, nome, bairro: 'Centro', latitude: 0, longitude: 0 }) as Ubs;
const med = (id: number, nome: string, concentracao: string) =>
  ({ id, nome, concentracao }) as Medicamento;
const desejo = (
  id: number,
  medicamentoId: number,
  medicamentoNome: string,
  ubsId = 1,
  notificar = true,
): ItemListaDesejo => ({
  id,
  ubsId,
  ubsNome: ubsId === 1 ? 'UBS Centro' : `UBS ${ubsId}`,
  medicamentoId,
  medicamentoNome,
  notificar,
  criadoEm: '',
});
const disp = (u: Ubs, status: StatusEstoque, quantidade: number) => ({
  ubs: u,
  status,
  quantidade,
  atualizadoEm: '',
});

describe('ListaPage', () => {
  let http: HttpTestingController;

  function preparar(localizacao = false, ubsReferenciaId: number | null = 1) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId, remediosContinuos: [], buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok, erro) =>
      localizacao
        ? ok({ coords: { latitude: 0, longitude: 0 } } as GeolocationPosition)
        : erro!({ code: 1 } as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  /** Abre a página com Metformina (em falta na UBS Centro) e Losartana (disponível nela). */
  function abrir(localizacao = false) {
    preparar(localizacao);
    const fixture = TestBed.createComponent(ListaPage);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    http.expectOne('/api/ubs/1').flush(ubs(1, 'UBS Centro'));
    http
      .expectOne('/api/lista-desejos')
      .flush([desejo(10, 1, 'Metformina'), desejo(11, 2, 'Losartana', 1, false)]);
    http.expectOne('/api/medicamentos/1').flush(med(1, 'Metformina', '850 mg'));
    http
      .expectOne('/api/medicamentos/1/disponibilidade')
      .flush([
        disp(ubs(5, 'UBS Longe'), 'DISPONIVEL', 40),
        disp(ubs(3, 'UBS Perto'), 'DISPONIVEL', 12),
        disp(ubs(1, 'UBS Centro'), 'EM_FALTA', 0),
      ]);
    http.expectOne('/api/medicamentos/2').flush(med(2, 'Losartana', '50 mg'));
    http
      .expectOne('/api/medicamentos/2/disponibilidade')
      .flush([disp(ubs(1, 'UBS Centro'), 'DISPONIVEL', 80)]);
    if (localizacao) {
      // Todas as UBS no mesmo ponto: a ordem só pode vir das distâncias da API.
      http.expectOne('/api/ubs?lat=0&lng=0').flush([
        { ...ubs(3, 'UBS Perto'), distanciaKm: 1.2 },
        { ...ubs(5, 'UBS Longe'), distanciaKm: 7.5 },
      ]);
    }
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const texto = (el: Element | null) => el!.textContent!.replace(/\s+/g, ' ').trim();
  const cartoes = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[aria-label="Remédios da lista"] > li')).slice(0, -1);
  const interruptor = (el: HTMLElement, nome: string) =>
    el.querySelector<HTMLButtonElement>(
      `[aria-label="Remédios da lista"] [role="switch"][aria-label="Avisar quando ${nome} chegar"]`,
    )!;

  it('mostra cada item com o status na UBS, a previsão e onde tem agora', fakeAsync(() => {
    const { el } = abrir();
    const [metformina, losartana] = cartoes(el).map(texto);
    expect(metformina).toContain('Metformina 850 mg');
    expect(metformina).toContain('UBS Centro');
    expect(metformina).toContain('Em falta');
    expect(metformina).toContain('Sem previsão de remessa');
    expect(losartana).toContain('Losartana 50 mg');
    expect(losartana).toContain('Disponível');
    expect(losartana).toContain('~80 un. em estoque');
    expect(losartana).toContain('Na própria UBS');
    expect(texto(el.querySelector('[data-aviso="referencia"]'))).toContain(
      'Novos remédios ficam vinculados à UBS Centro',
    );
  }));

  it('com localização, a alternativa é a UBS com estoque mais próxima segundo a API', fakeAsync(() => {
    const { el } = abrir(true);
    const link = cartoes(el)[0].querySelector<HTMLAnchorElement>('a[href^="/ubs/"]')!;
    expect(texto(link)).toBe('UBS Perto · 1,2 km');
    expect(link.getAttribute('href')).toBe('/ubs/3');
  }));

  it('liga e desliga o aviso pela API', fakeAsync(() => {
    const { fixture, el } = abrir();
    expect(interruptor(el, 'Losartana 50 mg').getAttribute('aria-checked')).toBe('false');

    interruptor(el, 'Losartana 50 mg').click();
    fixture.detectChanges();
    expect(interruptor(el, 'Losartana 50 mg').disabled).toBeTrue();
    const req = http.expectOne({ method: 'PATCH', url: '/api/lista-desejos/11' });
    expect(req.request.body).toEqual({ notificar: true });
    req.flush(desejo(11, 2, 'Losartana', 1, true));
    fixture.detectChanges();

    expect(interruptor(el, 'Losartana 50 mg').getAttribute('aria-checked')).toBe('true');
    expect(interruptor(el, 'Losartana 50 mg').disabled).toBeFalse();
    expect(texto(el.querySelector('[aria-live="polite"]'))).toBe(
      'Aviso ligado para Losartana 50 mg.',
    );
  }));

  it('remove o item pela API sem recarregar a lista', fakeAsync(() => {
    const { fixture, el } = abrir();
    el.querySelector<HTMLButtonElement>(
      '[aria-label="Remédios da lista"] [aria-label="Remover Metformina 850 mg da lista"]',
    )!.click();
    http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/10' }).flush(null);
    fixture.detectChanges();

    expect(cartoes(el).map((c) => texto(c.querySelector('h2')))).toEqual(['Losartana 50 mg']);
    expect(texto(el.querySelector('[aria-live="polite"]'))).toBe(
      'Metformina 850 mg removido da sua lista.',
    );
  }));

  it('avisa quando a alteração falha e mantém o item como estava', fakeAsync(() => {
    const { fixture, el } = abrir();
    interruptor(el, 'Metformina 850 mg').click();
    http.expectOne('/api/lista-desejos/10').flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();

    expect(texto(el.querySelector('[role="alert"]'))).toBe(
      'Não foi possível alterar Metformina 850 mg agora. Tente de novo.',
    );
    expect(interruptor(el, 'Metformina 850 mg').getAttribute('aria-checked')).toBe('true');
  }));

  it('mostra a lista vazia com o caminho para buscar', fakeAsync(() => {
    preparar(false, null);
    const fixture = TestBed.createComponent(ListaPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/lista-desejos').flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(texto(el)).toContain('Sua lista está vazia');
    expect(el.querySelector('a[href="/medicamentos"]')).not.toBeNull();
    expect(texto(el.querySelector('[data-aviso="referencia"]'))).toContain(
      'Escolha sua UBS de referência',
    );
  }));

  it('falha ao carregar permite tentar de novo', fakeAsync(() => {
    preparar(false, null);
    const fixture = TestBed.createComponent(ListaPage);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/lista-desejos').error(new ProgressEvent('error'));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(texto(el)).toContain('Não foi possível carregar sua lista agora');
    Array.from(el.querySelectorAll('button'))
      .find((b) => texto(b) === 'Tentar de novo')!
      .click();
    fixture.detectChanges();
    http.expectOne('/api/lista-desejos').flush([]);
    fixture.detectChanges();
    expect(texto(el)).toContain('Sua lista está vazia');
  }));
});
