import { environment } from '../../../environments/environment';
import { camadaDeTiles } from './mapa';

describe('camadaDeTiles', () => {
  it('usa a URL, a atribuição e o zoom máximo de environment.mapa', () => {
    const camada = camadaDeTiles();

    expect((camada as unknown as { _url: string })._url).toBe(environment.mapa.tilesUrl);
    expect(camada.getAttribution!()).toBe(environment.mapa.atribuicao);
    expect(camada.options.maxZoom).toBe(environment.mapa.maxZoom);
  });

  it('aceita outro provedor sem depender do OpenStreetMap', () => {
    const camada = camadaDeTiles({
      tilesUrl: 'https://tiles.exemplo.test/{z}/{x}/{y}.png',
      atribuicao: 'Exemplo',
      maxZoom: 17,
    });

    expect((camada as unknown as { _url: string })._url).toBe(
      'https://tiles.exemplo.test/{z}/{x}/{y}.png',
    );
    expect(camada.getAttribution!()).toBe('Exemplo');
    expect(camada.options.maxZoom).toBe(17);
  });
});
