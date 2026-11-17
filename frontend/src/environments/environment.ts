/**
 * Configuração do frontend (homologação e produção).
 * Em desenvolvimento, `ng serve` troca este arquivo por `environment.development.ts`.
 * Em produção, o frontend deve ser servido no mesmo domínio do backend (ou atrás de um reverse proxy).
 */
export const environment = {
  apiUrl: '/api',
  mapa: {
    // Tiles públicos do OpenStreetMap têm limite de uso e não servem para produção
    // (https://operations.osmfoundation.org/policies/tiles/). CARTO Voyager mantém o estilo
    // do OpenStreetMap, dispensa chave e só exige a atribuição abaixo.
    tilesUrl: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
    atribuicao:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
    maxZoom: 19,
  },
};
