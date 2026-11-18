/**
 * Configuração do frontend (homologação e produção).
 * Em desenvolvimento, `ng serve` troca este arquivo por `environment.development.ts`.
 * Em produção, o frontend deve ser servido no mesmo domínio do backend (ou atrás de um reverse proxy).
 */
export const environment = {
  apiUrl: '/api',
  mapa: {
    // Stadia Maps, estilo Alidade Smooth (claro e discreto, próximo do frame "Tablet paisagem ·
    // Mapa de UBS"). Não leva chave na URL: a Stadia autentica pelo domínio de origem, que precisa
    // estar cadastrado no painel (client.stadiamaps.com → Manage Properties → Authentication
    // Configuration). Sem isso, os tiles voltam 401 com uma imagem de aviso.
    // https://docs.stadiamaps.com/authentication/
    tilesUrl: 'https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png',
    atribuicao:
      '&copy; <a href="https://stadiamaps.com/attribution/" target="_blank" rel="noopener">Stadia Maps</a> ' +
      '&copy; <a href="https://openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> ' +
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
    maxZoom: 20,
  },
};
