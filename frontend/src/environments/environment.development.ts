/**
 * Configuração de desenvolvimento (`ng serve`), trocada no lugar de `environment.ts` pelo
 * `fileReplacements` do angular.json. Por isso não importa `environment.ts`: o import cairia neste
 * mesmo arquivo.
 * `/api` é encaminhado para o backend (http://localhost:8080) pelo proxy.conf.json.
 */
export const environment = {
  apiUrl: '/api',
  mapa: {
    // Uso local é baixo e cabe na política dos tiles públicos do OpenStreetMap.
    tilesUrl: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    atribuicao:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
  },
};
