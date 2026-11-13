import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';

import { Coordenadas } from '../../core/geo';

export interface PinoMapa {
  id: number;
  latitude: number;
  longitude: number;
  /** Cor de preenchimento (valor CSS). */
  cor: string;
  rotulo: string;
}

/** Mapa (Leaflet + OpenStreetMap) com pinos circulares e raio opcional. */
@Component({
  selector: 'app-mapa',
  host: { class: 'block' },
  template: `<div #alvo class="size-full rounded-card" role="region" aria-label="Mapa"></div>`,
})
export class Mapa {
  readonly pinos = input<PinoMapa[]>([]);
  readonly centro = input<Coordenadas | null>(null);
  readonly raioKm = input<number | null>(null);
  readonly selecionado = input<number | null>(null);
  readonly selecionar = output<number>();

  private readonly alvo = viewChild.required<ElementRef<HTMLElement>>('alvo');
  private mapa: L.Map | null = null;
  private readonly camada = L.layerGroup();

  constructor() {
    afterNextRender(() => {
      this.mapa = L.map(this.alvo().nativeElement, { zoomControl: true }).setView(
        [-9.65, -35.73],
        12,
      );
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap',
      }).addTo(this.mapa);
      this.camada.addTo(this.mapa);
      this.desenhar();
    });
    inject(DestroyRef).onDestroy(() => this.mapa?.remove());
    effect(() => this.desenhar());
  }

  private desenhar(): void {
    const pinos = this.pinos();
    const centro = this.centro();
    const raio = this.raioKm();
    const sel = this.selecionado();
    if (!this.mapa) return;

    this.camada.clearLayers();
    for (const p of pinos) {
      L.circleMarker([p.latitude, p.longitude], {
        radius: p.id === sel ? 11 : 8,
        color: '#ffffff',
        weight: p.id === sel ? 3 : 2,
        fillColor: p.cor,
        fillOpacity: 1,
      })
        .bindTooltip(p.rotulo)
        .on('click', () => this.selecionar.emit(p.id))
        .addTo(this.camada);
    }

    if (centro) {
      L.circleMarker([centro.latitude, centro.longitude], {
        radius: 6,
        color: '#1d4ed8',
        fillColor: '#1d4ed8',
        fillOpacity: 1,
      })
        .bindTooltip('Você está aqui')
        .addTo(this.camada);
    }

    if (centro && raio) {
      const circulo = L.circle([centro.latitude, centro.longitude], {
        radius: raio * 1000,
        color: '#0f766e',
        weight: 1,
        fillOpacity: 0.05,
      }).addTo(this.camada);
      this.mapa.fitBounds(circulo.getBounds(), { padding: [16, 16] });
    } else if (pinos.length) {
      this.mapa.fitBounds(
        L.latLngBounds(pinos.map((p) => [p.latitude, p.longitude] as L.LatLngTuple)),
        { padding: [24, 24], maxZoom: 15 },
      );
    }
  }
}
