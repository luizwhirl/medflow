import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { destinoSeguro, exigirBoasVindas } from '../../core/guards/boas-vindas.guard';
import { Ubs } from '../../core/models/ubs.model';
import { PreferenciasService } from '../../core/services/preferencias.service';
import { BoasVindasPage } from './boas-vindas.page';

const ubs = (id: number, bairro: string) => ({ id, nome: `UBS ${id}`, bairro }) as Ubs;

function salvarPreferencias(parcial: object) {
  localStorage.setItem(
    'medflow.preferencias',
    JSON.stringify({
      ubsReferenciaId: null,
      remediosContinuos: [],
      buscasRecentes: [],
      ...parcial,
    }),
  );
}

describe('exigirBoasVindas', () => {
  function rodar(url: string) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    return TestBed.runInInjectionContext(() =>
      exigirBoasVindas({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }
  const url = (r: unknown) => TestBed.inject(Router).serializeUrl(r as UrlTree);

  it('na primeira visita leva à boas-vindas guardando a rota pedida', () => {
    salvarPreferencias({});
    expect(url(rodar('/ubs/3'))).toBe('/boas-vindas?voltar=%2Fubs%2F3');
  });

  it('vindo da Início, não repete a rota em "voltar"', () => {
    salvarPreferencias({});
    expect(url(rodar('/'))).toBe('/boas-vindas');
  });

  it('deixa passar quem concluiu ou já tinha UBS de referência', () => {
    salvarPreferencias({ boasVindasConcluidas: true });
    expect(rodar('/mapa')).toBeTrue();

    TestBed.resetTestingModule();
    salvarPreferencias({ ubsReferenciaId: 4 });
    expect(rodar('/mapa')).toBeTrue();
  });
});

describe('destinoSeguro', () => {
  it('só aceita rotas internas', () => {
    expect(destinoSeguro('/ubs/3?x=1')).toBe('/ubs/3?x=1');
    expect(destinoSeguro('//site.com')).toBe('/');
    expect(destinoSeguro('https://site.com')).toBe('/');
    expect(destinoSeguro(undefined)).toBe('/');
  });
});

describe('BoasVindasPage', () => {
  let http: HttpTestingController;
  let navegar: jasmine.Spy;

  function abrir(localizacao: 'ok' | 'negada' = 'negada', voltar?: string) {
    salvarPreferencias({});
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((ok, erro) =>
      localizacao === 'ok'
        ? ok({ coords: { latitude: -9.6, longitude: -35.7 } } as GeolocationPosition)
        : erro!({ code: 1 } as GeolocationPositionError),
    );
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    const fixture = TestBed.createComponent(BoasVindasPage);
    if (voltar) fixture.componentRef.setInput('voltar', voltar);
    fixture.detectChanges();
    http.expectOne('/api/ubs/bairros').flush(['Benedito Bentes', 'Clima Bom']);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => http.verify());

  const botao = (el: HTMLElement, texto: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.trim() === texto)!;
  const prefs = () => TestBed.inject(PreferenciasService);

  it('lista os bairros da API', () => {
    const { el } = abrir();
    const opcoes = Array.from(el.querySelectorAll('#bairro option')).map((o) =>
      o.textContent!.trim(),
    );
    expect(opcoes).toEqual(['Selecione', 'Benedito Bentes', 'Clima Bom']);
  });

  it('com o bairro, a primeira UBS dele vira a de referência e volta para a rota pedida', fakeAsync(() => {
    const { fixture, el } = abrir('negada', '/ubs/3');
    const select = el.querySelector<HTMLSelectElement>('#bairro')!;
    select.value = 'Clima Bom';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    botao(el, 'OK').click();
    http
      .expectOne('/api/ubs')
      .flush([ubs(1, 'Benedito Bentes'), ubs(5, 'Clima Bom'), ubs(6, 'Clima Bom')]);
    flushMicrotasks();

    expect(prefs().ubsReferenciaId()).toBe(5);
    expect(prefs().boasVindasConcluidas()).toBeTrue();
    expect(navegar).toHaveBeenCalledWith('/ubs/3', { replaceUrl: true });
  }));

  it('com a localização, a UBS mais próxima segundo a API vira a de referência', fakeAsync(() => {
    const { el } = abrir('ok');
    botao(el, 'Usar minha localização').click();
    flushMicrotasks();
    http.expectOne('/api/ubs?lat=-9.6&lng=-35.7').flush([
      { ...ubs(7, 'Riacho Doce'), distanciaKm: 0.4 },
      { ...ubs(1, 'Benedito Bentes'), distanciaKm: 6.2 },
    ]);
    flushMicrotasks();

    expect(prefs().ubsReferenciaId()).toBe(7);
    expect(navegar).toHaveBeenCalledWith('/', { replaceUrl: true });
  }));

  it('localização negada mostra o caminho pelo bairro e não conclui', fakeAsync(() => {
    const { fixture, el } = abrir('negada');
    botao(el, 'Usar minha localização').click();
    flushMicrotasks();
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Escolha seu bairro abaixo');
    expect(prefs().boasVindasConcluidas()).toBeFalse();
    expect(navegar).not.toHaveBeenCalled();
  }));

  it('sem UBS a até 20 km, pede o bairro', fakeAsync(() => {
    const { fixture, el } = abrir('ok');
    botao(el, 'Usar minha localização').click();
    flushMicrotasks();
    http.expectOne('/api/ubs?lat=-9.6&lng=-35.7').flush([]);
    flushMicrotasks();
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Nenhuma UBS a até 20 km');
    expect(prefs().ubsReferenciaId()).toBeNull();
  }));

  it('"Agora não" conclui sem escolher UBS', () => {
    const { el } = abrir();
    botao(el, 'Agora não').click();
    expect(prefs().boasVindasConcluidas()).toBeTrue();
    expect(prefs().ubsReferenciaId()).toBeNull();
    expect(navegar).toHaveBeenCalledWith('/', { replaceUrl: true });
  });
});
