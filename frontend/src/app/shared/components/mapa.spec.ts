import * as L from 'leaflet';

import { environment } from '../../../environments/environment';
import { camadaDeTiles } from './mapa';

/**
 * URL do tile (x=1, y=2, z=3) pela API pública do Leaflet (`getTileUrl`), sem ler campos internos.
 * O zoom vem do mapa em que a camada está, então ela é presa a um mapa sem tamanho (não baixa tiles).
 */
function urlDoTile(camada: L.TileLayer): string {
  const mapa = L.map(document.createElement('div')).setView([0, 0], 3);
  camada.addTo(mapa);
  const url = camada.getTileUrl({ x: 1, y: 2, z: 3 } as L.Coords);
  mapa.remove();
  return url;
}

describe('camadaDeTiles', () => {
  it('usa a URL, a atribuição e o zoom máximo de environment.mapa', () => {
    const camada = camadaDeTiles();

    // x + y = 3 escolhe o primeiro subdomínio ("a"); sem retina, {r} fica vazio.
    expect(urlDoTile(camada)).toBe(
      L.Util.template(environment.mapa.tilesUrl, { s: 'a', x: 1, y: 2, z: 3, r: '' }),
    );
    expect(camada.getAttribution!()).toBe(environment.mapa.atribuicao);
    expect(camada.options.maxZoom).toBe(environment.mapa.maxZoom);
  });

  it('aceita outro provedor sem depender do OpenStreetMap', () => {
    const camada = camadaDeTiles({
      tilesUrl: 'https://tiles.exemplo.test/{z}/{x}/{y}.png',
      atribuicao: 'Exemplo',
      maxZoom: 17,
    });

    expect(urlDoTile(camada)).toBe('https://tiles.exemplo.test/3/1/2.png');
    expect(camada.getAttribution!()).toBe('Exemplo');
    expect(camada.options.maxZoom).toBe(17);
  });
});
