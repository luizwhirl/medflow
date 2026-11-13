export interface Coordenadas {
  latitude: number;
  longitude: number;
}

/** Distância em km entre dois pontos (fórmula de Haversine). */
export function distanciaKm(a: Coordenadas, b: Coordenadas): number {
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLng = rad(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

/** Posição atual do navegador, ou `null` se negada/indisponível. */
export function posicaoAtual(): Promise<Coordenadas | null> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) return Promise.resolve(null);
  return new Promise((resolve) =>
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude }),
      () => resolve(null),
      { timeout: 8000, maximumAge: 300_000 },
    ),
  );
}

/** "260 m" abaixo de 1 km; "2,4 km" acima. */
export function formatarDistancia(km: number): string {
  return km < 1
    ? `${Math.round((km * 1000) / 10) * 10} m`
    : `${km.toLocaleString('pt-BR', { maximumFractionDigits: 1, minimumFractionDigits: 1 })} km`;
}

/** Link de rota no Google Maps até o ponto. */
export function linkRota(destino: Coordenadas): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${destino.latitude},${destino.longitude}`;
}
