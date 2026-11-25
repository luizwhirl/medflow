import {
  HttpClient,
  HttpParams,
  HttpRequest,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConexaoService } from './conexao.service';
import { chaveDe, dadosSalvosInterceptor } from './dados-salvos.interceptor';
import {
  DadosSalvosService,
  MAX_BUSCAS_SALVAS,
  PREFIXO_DADOS_SALVOS,
} from './dados-salvos.service';

describe('dadosSalvosInterceptor', () => {
  let http: HttpClient;
  let api: HttpTestingController;
  let conexao: ConexaoService;
  let salvos: DadosSalvosService;

  const limpar = () =>
    Object.keys(localStorage)
      .filter((k) => k.startsWith(PREFIXO_DADOS_SALVOS))
      .forEach((k) => localStorage.removeItem(k));

  beforeEach(() => {
    limpar();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([dadosSalvosInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
    conexao = TestBed.inject(ConexaoService);
    salvos = TestBed.inject(DadosSalvosService);
  });

  afterEach(() => {
    api.verify();
    limpar();
  });

  it('salva a resposta e, sem conexão, devolve a versão salva com o horário', () => {
    http.get('/api/ubs/7').subscribe();
    api.expectOne('/api/ubs/7').flush({ id: 7, nome: 'UBS Clima Bom' });
    const salvoEm = salvos.ler('/api/ubs/7')!.em;
    expect(conexao.dadosSalvosEm()).toBeNull();

    let resposta: unknown;
    http.get('/api/ubs/7').subscribe((r) => (resposta = r));
    api.expectOne('/api/ubs/7').error(new ProgressEvent('error'));

    expect(resposta).toEqual({ id: 7, nome: 'UBS Clima Bom' });
    expect(conexao.dadosSalvosEm()).toBe(salvoEm);
  });

  it('com a API fora do ar (503) também usa o que está salvo', () => {
    salvos.salvar('/api/medicamentos/categorias', ['Diabetes'], 1_000);
    let resposta: unknown;
    http.get('/api/medicamentos/categorias').subscribe((r) => (resposta = r));
    api
      .expectOne('/api/medicamentos/categorias')
      .flush(null, { status: 503, statusText: 'Indisponível' });
    expect(resposta).toEqual(['Diabetes']);
    expect(conexao.dadosSalvosEm()).toBe(1_000);
  });

  it('sem nada salvo, o erro chega à tela', () => {
    let erro: unknown;
    http.get('/api/ubs/8').subscribe({ error: (e) => (erro = e) });
    api.expectOne('/api/ubs/8').error(new ProgressEvent('error'));
    expect(erro).toBeDefined();
    expect(conexao.dadosSalvosEm()).toBeNull();
  });

  it('não esconde erros da API (404, 400) com dados salvos', () => {
    salvos.salvar('/api/ubs/9', { id: 9 });
    let status = 0;
    http.get('/api/ubs/9').subscribe({ error: (e) => (status = e.status) });
    api.expectOne('/api/ubs/9').flush(null, { status: 404, statusText: 'Não encontrado' });
    expect(status).toBe(404);
    expect(conexao.dadosSalvosEm()).toBeNull();
  });

  it('guarda o horário mais antigo entre as respostas salvas usadas', () => {
    salvos.salvar('/api/a', 1, 5_000);
    salvos.salvar('/api/b', 2, 2_000);
    http.get('/api/a').subscribe();
    http.get('/api/b').subscribe();
    api.expectOne('/api/a').error(new ProgressEvent('error'));
    api.expectOne('/api/b').error(new ProgressEvent('error'));
    expect(conexao.dadosSalvosEm()).toBe(2_000);
  });

  it('uma alteração bem-sucedida descarta o que estava salvo do mesmo recurso', () => {
    salvos.salvar('/api/lista-desejos', [{ id: 1 }]);
    salvos.salvar('/api/ubs', [{ id: 7 }]);
    http.delete('/api/lista-desejos/1').subscribe();
    api.expectOne('/api/lista-desejos/1').flush(null, { status: 204, statusText: 'Sem conteúdo' });
    expect(salvos.ler('/api/lista-desejos')).toBeNull();
    expect(salvos.ler('/api/ubs')).not.toBeNull();
  });

  it('só trata chamadas à API', () => {
    http.get('/icons/marca.svg', { responseType: 'text' }).subscribe();
    api.expectOne('/icons/marca.svg').flush('<svg/>');
    expect(Object.keys(localStorage).some((k) => k.startsWith(PREFIXO_DADOS_SALVOS))).toBeFalse();
  });

  it('não salva nem usa dados salvos da gestão e da autenticação (dependem de quem está logado)', () => {
    http.get('/api/gestao/indicadores').subscribe();
    api.expectOne('/api/gestao/indicadores').flush({ ubsComEstoqueBaixo: 3 });
    http.get('/api/auth/eu').subscribe();
    api.expectOne('/api/auth/eu').flush({ email: 'gestor@ubs.br' });
    expect(Object.keys(localStorage).some((k) => k.startsWith(PREFIXO_DADOS_SALVOS))).toBeFalse();

    // Mesmo que algo tenha ficado salvo, sem conexão o erro chega à tela.
    salvos.salvar('/api/gestao/indicadores', { ubsComEstoqueBaixo: 9 });
    let erro: unknown;
    http.get('/api/gestao/indicadores').subscribe({ error: (e) => (erro = e) });
    api.expectOne('/api/gestao/indicadores').error(new ProgressEvent('error'));
    expect(erro).toBeDefined();
    expect(conexao.dadosSalvosEm()).toBeNull();
  });

  it('guarda só as buscas mais recentes', () => {
    for (let i = 0; i < MAX_BUSCAS_SALVAS + 2; i++) {
      salvos.salvar(`/api/medicamentos?busca=termo${i}`, [], 1_000 + i);
    }
    salvos.salvar('/api/medicamentos/categorias', [], 1);

    expect(salvos.ler('/api/medicamentos?busca=termo0')).toBeNull();
    expect(salvos.ler('/api/medicamentos?busca=termo1')).toBeNull();
    expect(salvos.ler('/api/medicamentos?busca=termo2')).not.toBeNull();
    expect(salvos.ler(`/api/medicamentos?busca=termo${MAX_BUSCAS_SALVAS + 1}`)).not.toBeNull();
    // O que não é busca não entra na conta, mesmo sendo mais antigo.
    expect(salvos.ler('/api/medicamentos/categorias')).not.toBeNull();
  });

  it('a chave ordena os parâmetros e ignora as coordenadas', () => {
    const req = (params: Record<string, string | number>) =>
      new HttpRequest('GET', '/api/ubs', null, { params: new HttpParams({ fromObject: params }) });
    expect(chaveDe(req({ raioKm: 6, lat: -9.6, lng: -35.7 }))).toBe('/api/ubs?raioKm=6');
    expect(chaveDe(req({ medicamentoId: 3, raioKm: 6 }))).toBe(
      chaveDe(req({ raioKm: 6, medicamentoId: 3 })),
    );
    expect(chaveDe(new HttpRequest('GET', '/api/medicamentos?nome=a&pagina=0'))).toBe(
      '/api/medicamentos?nome=a&pagina=0',
    );
  });
});
