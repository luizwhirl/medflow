import { Injectable } from '@angular/core';

const CHAVE = 'medflow.dispositivo';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Identificador aleatório deste aparelho (UUID v4), usado no lugar do login para a lista de
 * desejos (cabeçalho `X-Dispositivo-Id`). É gerado uma vez e guardado no navegador; quem tem o
 * identificador tem acesso à lista, então ele nunca é exibido nem enviado a outras rotas.
 */
@Injectable({ providedIn: 'root' })
export class DispositivoService {
  private id: string | null = null;

  identificador(): string {
    if (this.id) return this.id;
    let salvo: string | null = null;
    try {
      salvo = localStorage.getItem(CHAVE);
    } catch {
      // Armazenamento indisponível (aba privada): usa um identificador só desta sessão.
    }
    if (!salvo || !UUID.test(salvo)) {
      salvo = novoUuid();
      try {
        localStorage.setItem(CHAVE, salvo);
      } catch {
        // Idem: a lista vale enquanto a aba estiver aberta.
      }
    }
    return (this.id = salvo);
  }
}

/** `crypto.randomUUID` só existe em contexto seguro (HTTPS ou localhost); fora dele, monta o v4. */
function novoUuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; // versão 4
  b[8] = (b[8] & 0x3f) | 0x80; // variante RFC 4122
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
