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
  /** Número exibido no pino (posição na lista). */
  numero?: number;
}

/** Mapa (Leaflet + OpenStreetMap) com pinos numerados e raio opcional. */
@Component({
  selector: 'app-mapa',
  host: { class: 'block' },
  template: `<div #alvo class="size-full" role="region" aria-label="Mapa"></div>`,
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
      this.mapa = L.map(this.alvo().nativeElement, { zoomControl: false }).setView(
        [-9.65, -35.73],
        12,
      );
      L.control.zoom({ position: 'topright' }).addTo(this.mapa);
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

    if (centro && raio) {
      L.circle([centro.latitude, centro.longitude], {
        radius: raio * 1000,
        color: '#0f766e',
        weight: 1,
        fillOpacity: 0.04,
      }).addTo(this.camada);
    }

    for (const p of pinos) {
      const tam = p.id === sel ? 44 : 36;
      L.marker([p.latitude, p.longitude], {
        title: p.rotulo,
        zIndexOffset: p.id === sel ? 1000 : 0,
        icon: L.divIcon({
          className: '',
          iconSize: [tam, tam],
          iconAnchor: [tam / 2, tam / 2],
          html: `<span style="display:grid;place-items:center;width:${tam}px;height:${tam}px;border-radius:9999px;background:${p.cor};color:#fff;font-weight:700;font-size:15px;border:3px solid #fff;box-shadow:0 1px 4px rgb(15 23 42/25%)">${p.numero ?? ''}</span>`,
        }),
      })
        .on('click', () => this.selecionar.emit(p.id))
        .addTo(this.camada);
    }

    if (centro) {
      L.circleMarker([centro.latitude, centro.longitude], {
        radius: 8,
        color: '#ffffff',
        weight: 3,
        fillColor: '#1d4ed8',
        fillOpacity: 1,
      })
        .bindTooltip('Você está aqui')
        .addTo(this.camada);
    }

    const pontos = pinos.map((p) => [p.latitude, p.longitude] as L.LatLngTuple);
    if (centro) pontos.push([centro.latitude, centro.longitude]);
    if (pontos.length) {
      this.mapa.fitBounds(L.latLngBounds(pontos), {
        paddingTopLeft: [32, 32],
        paddingBottomRight: [32, 260],
        maxZoom: 15,
      });
    }
  }
}
