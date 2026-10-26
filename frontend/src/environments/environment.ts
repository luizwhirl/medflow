/**
 * Configuração do frontend.
 * Em desenvolvimento, `/api` é encaminhado para o backend (http://localhost:8080) pelo proxy.conf.json.
 * Em produção, o frontend deve ser servido no mesmo domínio do backend (ou atrás de um reverse proxy).
 */
export const environment = {
  apiUrl: '/api',
};
