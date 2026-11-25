import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, of, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ConexaoService } from './conexao.service';
import { DadosSalvosService } from './dados-salvos.service';

/**
 * Coordenadas variam um pouco a cada leitura do GPS; sem elas na chave, "UBS perto de mim" salvo
 * continua valendo sem internet mesmo que a posição tenha mudado alguns metros.
 */
const PARAMETROS_IGNORADOS = new Set(['lat', 'lng']);

/** Sem rede (0) ou com o servidor/proxy fora do ar (502–504): vale mostrar o que está salvo. */
const STATUS_SEM_CONEXAO = new Set([0, 502, 503, 504]);

/**
 * Rotas que dependem de quem está logado: não vão para o navegador, que sobrevive ao logout e não
 * separa contas (o gestor de uma UBS veria, sem internet, os números salvos por outro gestor).
 * Sem internet, essas telas mostram o erro padrão.
 */
const ROTAS_NAO_SALVAS = ['gestao', 'auth'].map((r) => `${environment.apiUrl}/${r}`);

/**
 * Guarda a resposta de cada leitura pública da API (e da lista de desejos do aparelho) e, quando a
 * API não responde, devolve a última resposta salva no lugar do erro, avisando o
 * {@link ConexaoService} (que mostra o banner com o horário).
 * Uma escrita bem-sucedida (POST/PATCH/DELETE) descarta o que estava salvo daquele recurso, para
 * não mostrar depois uma versão anterior à alteração.
 */
export const dadosSalvosInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl) || naoSalva(req.url)) return next(req);
  const salvos = inject(DadosSalvosService);

  if (req.method !== 'GET') {
    return next(req).pipe(
      tap((evento) => {
        if (evento instanceof HttpResponse && evento.ok) salvos.descartar(recurso(req.url));
      }),
    );
  }

  const conexao = inject(ConexaoService);
  const chave = chaveDe(req);
  return next(req).pipe(
    tap((evento) => {
      if (evento instanceof HttpResponse && evento.ok) salvos.salvar(chave, evento.body);
    }),
    catchError((erro: unknown) => {
      const salvo =
        erro instanceof HttpErrorResponse && STATUS_SEM_CONEXAO.has(erro.status)
          ? salvos.ler(chave)
          : null;
      if (!salvo) return throwError(() => erro);
      conexao.registrarDadosSalvos(salvo.em);
      return of(new HttpResponse({ body: salvo.corpo, status: 200, url: req.urlWithParams }));
    }),
  );
};

/** Caminho + parâmetros em ordem alfabética (sem as coordenadas). */
export function chaveDe(req: HttpRequest<unknown>): string {
  const [caminho, busca = ''] = req.url.split('?');
  const params = new URLSearchParams(busca);
  for (const nome of req.params.keys()) {
    for (const valor of req.params.getAll(nome) ?? []) params.append(nome, valor);
  }
  const pares = [...params.entries()]
    .filter(([nome]) => !PARAMETROS_IGNORADOS.has(nome))
    .sort(([a, va], [b, vb]) => a.localeCompare(b) || va.localeCompare(vb))
    .map(([nome, valor]) => `${nome}=${valor}`);
  return pares.length ? `${caminho}?${pares.join('&')}` : caminho;
}

function naoSalva(url: string): boolean {
  const caminho = url.split('?')[0];
  return ROTAS_NAO_SALVAS.some((r) => caminho === r || caminho.startsWith(`${r}/`));
}

/** `/api/lista-desejos/7` → `/api/lista-desejos`. */
function recurso(url: string): string {
  const caminho = url.split('?')[0];
  const base = environment.apiUrl.replace(/\/$/, '');
  const [primeiro] = caminho.slice(base.length).split('/').filter(Boolean);
  return primeiro ? `${base}/${primeiro}` : base;
}
