import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ItemListaDesejo } from '../../core/models/lista-desejos.model';
import { Medicamento } from '../../core/models/medicamento.model';
import { RastreioMedicamento } from './rastreio-medicamento';

const desejo = (ubsId: number, notificar: boolean): ItemListaDesejo => ({
  id: 40,
  ubsId,
  ubsNome: `UBS ${ubsId}`,
  medicamentoId: 7,
  medicamentoNome: 'Metformina',
  medicamentoConcentracao: '850 mg',
  notificar,
  criadoEm: '',
});

describe('RastreioMedicamento · Avisar quando chegar', () => {
  let http: HttpTestingController;

  function abrir(ubsReferenciaId: number | null, lista: ItemListaDesejo[]) {
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId, buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((_ok, erro) =>
      erro!({ code: 1 } as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(RastreioMedicamento);
    fixture.componentRef.setInput('id', 7);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/lista-desejos').flush(lista);
    http
      .expectOne('/api/medicamentos/7')
      .flush({ id: 7, nome: 'Metformina', concentracao: '850 mg' } as Medicamento);
    http.expectOne('/api/medicamentos/7/disponibilidade').flush([]);
    http.expectOne('/api/ubs').flush([]);
    fixture.detectChanges();
    semRemessa();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  /** Responde que não há remessa (o rastreamento só é pedido com UBS de referência). */
  function semRemessa() {
    for (const req of http.match((r) => r.url.endsWith('/rastreamento'))) {
      req.flush({
        medicamentoId: 7,
        ubsId: Number(req.request.params.get('ubsId')),
        remessa: null,
      });
    }
  }

  afterEach(() => http.verify());

  const botao = (el: HTMLElement) =>
    el.querySelector<HTMLButtonElement>('button[aria-describedby="aviso-ajuda"]')!;
  const ajuda = (el: HTMLElement) =>
    el.querySelector('#aviso-ajuda')!.textContent!.replace(/\s+/g, ' ').trim();

  it('cria o item na UBS de referência e passa a mostrar o aviso ligado', fakeAsync(() => {
    const { fixture, el } = abrir(1, []);
    expect(botao(el).textContent).toContain('Avisar quando chegar');
    expect(ajuda(el)).toBe('O aviso fica vinculado à sua UBS de referência.');

    botao(el).click();
    const req = http.expectOne({ method: 'POST', url: '/api/lista-desejos' });
    expect(req.request.body).toEqual({ ubsId: 1, medicamentoId: 7, notificar: true });
    req.flush(desejo(1, true));
    fixture.detectChanges();

    expect(botao(el).textContent).toContain('Aviso ligado');
    expect(botao(el).getAttribute('aria-pressed')).toBe('true');
    expect(ajuda(el)).toBe('Acompanhe em Minha lista.');
  }));

  it('com o item já na lista, desliga o aviso em vez de criar outro', fakeAsync(() => {
    const { fixture, el } = abrir(1, [desejo(1, true)]);
    expect(botao(el).textContent).toContain('Aviso ligado');

    botao(el).click();
    const req = http.expectOne({ method: 'PATCH', url: '/api/lista-desejos/40' });
    expect(req.request.body).toEqual({ notificar: false });
    req.flush(desejo(1, false));
    fixture.detectChanges();

    expect(botao(el).textContent).toContain('Avisar quando chegar');
    expect(botao(el).getAttribute('aria-pressed')).toBe('false');
  }));

  it('item de outra UBS não conta como aviso da UBS de referência', fakeAsync(() => {
    const { el } = abrir(1, [desejo(2, true)]);
    expect(botao(el).textContent).toContain('Avisar quando chegar');
  }));

  it('sem UBS de referência, desativa o botão e pede para escolher uma', fakeAsync(() => {
    const { el } = abrir(null, []);
    expect(botao(el).disabled).toBeTrue();
    expect(ajuda(el)).toBe('Escolha sua UBS para montar sua lista e receber avisos.');
    expect(el.querySelector('#aviso-ajuda a')!.getAttribute('href')).toBe('/mapa');
  }));

  it('mostra o erro se não conseguir salvar', fakeAsync(() => {
    const { fixture, el } = abrir(1, []);
    botao(el).click();
    http.expectOne('/api/lista-desejos').flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    expect(ajuda(el)).toBe('Não foi possível salvar o aviso agora. Tente de novo.');
    expect(botao(el).disabled).toBeFalse();
  }));

  it('se o item já existir (409), recarrega a lista e liga o aviso dele', fakeAsync(() => {
    // A lista não carregou: o botão segue habilitado e o clique tenta criar o item.
    localStorage.setItem(
      'medflow.preferencias',
      JSON.stringify({ ubsReferenciaId: 1, buscasRecentes: [] }),
    );
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((_ok, erro) =>
      erro!({ code: 1 } as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RastreioMedicamento);
    fixture.componentRef.setInput('id', 7);
    fixture.detectChanges();
    flushMicrotasks();
    http.expectOne('/api/lista-desejos').error(new ProgressEvent('error'));
    http
      .expectOne('/api/medicamentos/7')
      .flush({ id: 7, nome: 'Metformina', concentracao: '850 mg' } as Medicamento);
    http.expectOne('/api/medicamentos/7/disponibilidade').flush([]);
    http.expectOne('/api/ubs').flush([]);
    fixture.detectChanges();
    semRemessa();
    const el = fixture.nativeElement as HTMLElement;
    expect(botao(el).disabled).toBeFalse();

    botao(el).click();
    http
      .expectOne({ method: 'POST', url: '/api/lista-desejos' })
      .flush(null, { status: 409, statusText: 'Conflict' });
    http.expectOne({ method: 'GET', url: '/api/lista-desejos' }).flush([desejo(1, false)]);
    const req = http.expectOne({ method: 'PATCH', url: '/api/lista-desejos/40' });
    expect(req.request.body).toEqual({ notificar: true });
    req.flush(desejo(1, true));
    fixture.detectChanges();

    expect(botao(el).textContent).toContain('Aviso ligado');
    expect(ajuda(el)).toBe('Acompanhe em Minha lista.');
  }));

  const usoContinuo = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('button')).find((b) => /uso contínuo/i.test(b.textContent!))!;

  it('marcar uso contínuo põe o remédio na Minha lista, sem ligar o aviso', fakeAsync(() => {
    const { fixture, el } = abrir(1, []);
    expect(usoContinuo(el).textContent!.trim()).toBe('Marcar uso contínuo');

    usoContinuo(el).click();
    const req = http.expectOne({ method: 'POST', url: '/api/lista-desejos' });
    expect(req.request.body).toEqual({ ubsId: 1, medicamentoId: 7, notificar: false });
    req.flush(desejo(1, false));
    fixture.detectChanges();

    expect(usoContinuo(el).textContent!.trim()).toBe('Uso contínuo');
    expect(usoContinuo(el).getAttribute('aria-pressed')).toBe('true');
    expect(botao(el).textContent).toContain('Avisar quando chegar');
  }));

  it('desmarcar uso contínuo tira o remédio da lista em todas as UBS', fakeAsync(() => {
    const { fixture, el } = abrir(1, [desejo(1, true), { ...desejo(2, false), id: 41 }]);
    expect(usoContinuo(el).getAttribute('aria-pressed')).toBe('true');

    usoContinuo(el).click();
    http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/40' }).flush(null);
    http.expectOne({ method: 'DELETE', url: '/api/lista-desejos/41' }).flush(null);
    fixture.detectChanges();

    expect(usoContinuo(el).textContent!.trim()).toBe('Marcar uso contínuo');
    expect(botao(el).textContent).toContain('Avisar quando chegar');
  }));

  it('sem UBS de referência, só desmarca o que já está na lista', fakeAsync(() => {
    const { el } = abrir(null, []);
    expect(usoContinuo(el).disabled).toBeTrue();
  }));
});
