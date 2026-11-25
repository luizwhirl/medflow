import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';

export type Papel = 'CIDADAO' | 'GESTOR';

/** Resposta de `POST /api/auth/login`. */
interface TokenResponse {
  token: string;
  tipo: string;
  expiraEmSegundos: number;
  papel: Papel;
}

/** Resposta de `GET /api/auth/eu`. */
export interface Conta {
  id: number;
  nome: string;
  email: string;
  papel: Papel;
}

interface Sessao {
  token: string;
  papel: Papel;
  /** Epoch em ms. */
  expiraEm: number;
}

const CHAVE = 'medflow.sessao';

/**
 * Sessão de quem entrou com e-mail e senha (hoje, só a área da gestão usa).
 * Fica no sessionStorage: some ao fechar a aba, e não vaza para outras abas nem para o cache offline.
 */
@Injectable({ providedIn: 'root' })
export class SessaoService {
  private readonly http = inject(HttpClient);
  private readonly atual = signal<Sessao | null>(lerGuardada());

  /** Token ainda válido, ou `null`. */
  readonly token = computed(() => {
    const s = this.atual();
    return s && s.expiraEm > Date.now() ? s.token : null;
  });

  readonly ehGestor = computed(() => this.token() !== null && this.atual()?.papel === 'GESTOR');

  entrar(email: string, senha: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${environment.apiUrl}/auth/login`, { email, senha }).pipe(
      tap((r) => {
        const sessao: Sessao = {
          token: r.token,
          papel: r.papel,
          expiraEm: Date.now() + r.expiraEmSegundos * 1000,
        };
        this.atual.set(sessao);
        guardar(sessao);
      }),
    );
  }

  conta(): Observable<Conta> {
    return this.http.get<Conta>(`${environment.apiUrl}/auth/eu`);
  }

  sair(): void {
    this.atual.set(null);
    guardar(null);
  }
}

function lerGuardada(): Sessao | null {
  try {
    const bruto = sessionStorage.getItem(CHAVE);
    const s = bruto ? (JSON.parse(bruto) as Sessao) : null;
    return s && typeof s.token === 'string' && s.expiraEm > Date.now() ? s : null;
  } catch {
    return null;
  }
}

function guardar(s: Sessao | null): void {
  try {
    if (s) sessionStorage.setItem(CHAVE, JSON.stringify(s));
    else sessionStorage.removeItem(CHAVE);
  } catch {
    // Navegador sem armazenamento (aba anônima restrita): a sessão vale só enquanto a página estiver aberta.
  }
}
