/**
 * Configuração de desenvolvimento (`ng serve`), trocada no lugar de `environment.ts` pelo
 * `fileReplacements` do angular.json. Por isso não importa `environment.ts`: o import cairia neste
 * mesmo arquivo.
 * `/api` é encaminhado para o backend (http://localhost:8080) pelo proxy.conf.json.
 */
export const environment = {
  apiUrl: '/api',
  mapa: {
    // Mesmo estilo de produção. Em localhost/127.0.0.1 a Stadia não exige autenticação
    // (com limite de uso mais baixo); https://docs.stadiamaps.com/authentication/
    tilesUrl: 'https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png',
    atribuicao:
      '&copy; <a href="https://stadiamaps.com/attribution/" target="_blank" rel="noopener">Stadia Maps</a> ' +
      '&copy; <a href="https://openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> ' +
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
    maxZoom: 20,
  },
};
