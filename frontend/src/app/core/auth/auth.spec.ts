import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { gestorGuard } from './gestor.guard';
import { SessaoService } from './sessao.service';

describe('Sessão do gestor', () => {
  let http: HttpTestingController;
  let sessao: SessaoService;

  beforeEach(() => {
    sessionStorage.removeItem('medflow.sessao');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    sessao = TestBed.inject(SessaoService);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.removeItem('medflow.sessao');
  });

  function entrarComo(papel: 'GESTOR' | 'CIDADAO', expiraEmSegundos = 3600): void {
    sessao.entrar('a@b.c', 'segredo').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'a@b.c', senha: 'segredo' });
    req.flush({ token: 'tok-123', tipo: 'Bearer', expiraEmSegundos, papel });
  }

  it('guarda o token do gestor na sessão da aba', () => {
    entrarComo('GESTOR');
    expect(sessao.ehGestor()).toBeTrue();
    expect(sessao.token()).toBe('tok-123');
    expect(sessionStorage.getItem('medflow.sessao')).toContain('tok-123');
  });

  it('conta de cidadão não é gestora', () => {
    entrarComo('CIDADAO');
    expect(sessao.ehGestor()).toBeFalse();
  });

  it('token vencido não vale', () => {
    entrarComo('GESTOR', -1);
    expect(sessao.token()).toBeNull();
    expect(sessao.ehGestor()).toBeFalse();
  });

  it('manda o token só nas rotas da gestão e na conta', () => {
    entrarComo('GESTOR');
    const cliente = TestBed.inject(HttpClient);
    cliente.get('/api/gestao/indicadores').subscribe();
    cliente.get('/api/auth/eu').subscribe();
    cliente.get('/api/ubs').subscribe();

    expect(http.expectOne('/api/gestao/indicadores').request.headers.get('Authorization')).toBe(
      'Bearer tok-123',
    );
    expect(http.expectOne('/api/auth/eu').request.headers.get('Authorization')).toBe(
      'Bearer tok-123',
    );
    expect(http.expectOne('/api/ubs').request.headers.has('Authorization')).toBeFalse();
  });

  it('401 na gestão encerra a sessão e leva para a entrada', () => {
    entrarComo('GESTOR');
    const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    TestBed.inject(HttpClient)
      .get('/api/gestao/indicadores')
      .subscribe({ error: () => undefined });
    http
      .expectOne('/api/gestao/indicadores')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(sessao.ehGestor()).toBeFalse();
    expect(navegar).toHaveBeenCalledWith(
      ['/gestao/entrar'],
      jasmine.objectContaining({ queryParams: jasmine.any(Object) }),
    );
  });

  it('guard deixa o gestor passar e manda os outros para a entrada, com a volta', () => {
    const rota = {} as ActivatedRouteSnapshot;
    const estado = { url: '/gestao/remessas' } as RouterStateSnapshot;

    const semSessao = TestBed.runInInjectionContext(() => gestorGuard(rota, estado)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(semSessao)).toBe(
      '/gestao/entrar?volta=%2Fgestao%2Fremessas',
    );

    entrarComo('GESTOR');
    expect(TestBed.runInInjectionContext(() => gestorGuard(rota, estado))).toBeTrue();
  });
});
