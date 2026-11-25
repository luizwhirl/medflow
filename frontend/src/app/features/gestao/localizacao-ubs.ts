import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';

import { Coordenadas, posicaoAtual } from '../../core/geo';
import { LocalizacaoUbs, OrigemLocalizacao } from '../../core/models/gestao.model';
import { GestaoService } from '../../core/services/gestao.service';
import { Button } from '../../shared/components/button';
import { Card } from '../../shared/components/card';
import { camadaDeTiles } from '../../shared/components/mapa';

const AVISO: Record<OrigemLocalizacao, { texto: string; classe: string }> = {
  FONTE: {
    texto:
      'Coordenada do CNES, conferida com o mapa do município. Se o pino não está na porta da unidade, ajuste.',
    classe: 'bg-status-info-subtle text-status-info',
  },
  LUGAR: {
    texto:
      'O CNES tinha a coordenada errada; a unidade foi achada pelo nome no OpenStreetMap. Confira o pino.',
    classe: 'bg-status-info-subtle text-status-info',
  },
  BAIRRO: {
    texto:
      'Localização aproximada (centro do bairro): o CNES tinha a coordenada errada. Arraste o pino até a unidade e salve.',
    classe: 'bg-status-alerta-subtle text-status-alerta',
  },
  MANUAL: {
    texto: 'Localização conferida pela gestão. A sincronização com a BNAFAR não muda este pino.',
    classe: 'bg-status-disponivel-subtle text-status-disponivel',
  },
  INVALIDA: {
    texto:
      'Sem localização confiável: a unidade não aparece no mapa nem na busca por perto. Arraste o pino até ela e salve.',
    classe: 'bg-status-falta-subtle text-status-falta',
  },
};

/** Pino da UBS no mapa do cidadão, com a correção pela gestão (arrastar ou usar a posição atual e salvar). */
@Component({
  selector: 'app-localizacao-ubs',
  imports: [Button, Card],
  template: `
    <app-card pad="none" class="flex flex-col gap-4 px-5 py-[1.125rem]">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex flex-col gap-0.5">
          <h2 class="text-[1.0625rem] font-bold text-content">Localização no mapa do app</h2>
          @if (local(); as l) {
            <p class="text-[0.8125rem] text-content-muted">{{ l.endereco }} · {{ l.bairro }}</p>
          }
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            appButton
            tamanho="sm"
            variante="secundario"
            type="button"
            (click)="usarPosicaoAtual()"
          >
            Usar minha localização atual
          </button>
          <button
            appButton
            tamanho="sm"
            type="button"
            [disabled]="!alterado() || salvando()"
            (click)="salvar()"
          >
            {{ salvando() ? 'Salvando…' : 'Salvar localização' }}
          </button>
        </div>
      </div>

      @if (local(); as l) {
        <p
          class="rounded-control px-3 py-2 text-[0.8125rem] font-medium"
          [class]="aviso().classe"
          data-teste="origem"
        >
          {{ aviso().texto }}
        </p>
      }
      @if (mensagem(); as m) {
        <p
          class="text-[0.8125rem]"
          [class]="m.erro ? 'text-status-falta' : 'text-status-disponivel'"
          role="status"
        >
          {{ m.texto }}
        </p>
      }

      <div
        #alvo
        class="h-72 overflow-hidden rounded-control border border-outline"
        role="region"
        aria-label="Mapa com o pino da UBS (arraste para corrigir)"
      ></div>
    </app-card>
  `,
})
export class LocalizacaoUbsCard {
  private readonly gestao = inject(GestaoService);

  readonly ubsId = input.required<number>();

  protected readonly local = signal<LocalizacaoUbs | null>(null);
  /** Onde o pino está agora (pode ter sido arrastado e ainda não salvo). */
  private readonly pino = signal<Coordenadas | null>(null);
  protected readonly salvando = signal(false);
  protected readonly mensagem = signal<{ texto: string; erro: boolean } | null>(null);

  protected readonly aviso = computed(() => AVISO[this.local()?.origem ?? 'FONTE']);
  protected readonly alterado = computed(() => {
    const l = this.local();
    const p = this.pino();
    return !!l && !!p && (l.latitude !== p.latitude || l.longitude !== p.longitude);
  });

  private readonly alvo = viewChild.required<ElementRef<HTMLElement>>('alvo');
  private mapa: L.Map | null = null;
  private marcador: L.Marker | null = null;

  constructor() {
    afterNextRender(() => {
      this.mapa = L.map(this.alvo().nativeElement).setView([-9.65, -35.73], 12);
      camadaDeTiles().addTo(this.mapa);
      this.mapa.on('click', (e: L.LeafletMouseEvent) =>
        this.mover({ latitude: e.latlng.lat, longitude: e.latlng.lng }),
      );
      this.desenhar();
    });
    inject(DestroyRef).onDestroy(() => this.mapa?.remove());

    // Só a troca de UBS recarrega: o resto fica fora do rastreio (senão mover o pino recarregaria e desfaria o movimento).
    effect(() => {
      const id = this.ubsId();
      untracked(() =>
        this.gestao.localizacao(id).subscribe({
          next: (l) => {
            this.local.set(l);
            this.pino.set({ latitude: l.latitude, longitude: l.longitude });
            this.desenhar(true);
          },
          error: () =>
            this.mensagem.set({
              texto: 'Não foi possível carregar a localização da UBS.',
              erro: true,
            }),
        }),
      );
    });
  }

  protected async usarPosicaoAtual(): Promise<void> {
    const p = await posicaoAtual();
    if (p) this.mover(p);
    else
      this.mensagem.set({
        texto: 'Não foi possível obter a sua localização (permita o acesso no navegador).',
        erro: true,
      });
  }

  protected salvar(): void {
    const p = this.pino();
    if (!p || this.salvando()) return;
    this.salvando.set(true);
    this.gestao.corrigirLocalizacao(this.ubsId(), p).subscribe({
      next: (l) => {
        this.salvando.set(false);
        this.local.set(l);
        this.pino.set({ latitude: l.latitude, longitude: l.longitude });
        this.mensagem.set({
          texto: 'Localização salva. O app já mostra a UBS neste ponto.',
          erro: false,
        });
      },
      error: (e: unknown) => {
        this.salvando.set(false);
        const detalhe =
          e instanceof HttpErrorResponse && e.status === 400
            ? (e.error?.detail as string | undefined)
            : undefined;
        this.mensagem.set({
          texto: detalhe ?? 'Não foi possível salvar a localização. Tente de novo.',
          erro: true,
        });
      },
    });
  }

  private mover(p: Coordenadas): void {
    this.pino.set(p);
    this.mensagem.set(null);
    this.desenhar();
  }

  private desenhar(centralizar = false): void {
    const p = this.pino();
    if (!this.mapa || !p) return;
    const ll: L.LatLngTuple = [p.latitude, p.longitude];
    if (!this.marcador) {
      this.marcador = L.marker(ll, {
        draggable: true,
        title: 'UBS',
        icon: L.divIcon({
          className: '',
          iconSize: [36, 36],
          iconAnchor: [18, 18],
          html: '<span style="display:block;width:36px;height:36px;border-radius:9999px;background:#0f766e;border:3px solid #fff;box-shadow:0 1px 4px rgb(15 23 42/25%)"></span>',
        }),
      })
        .on('dragend', () => {
          const pos = this.marcador!.getLatLng();
          this.mover({ latitude: pos.lat, longitude: pos.lng });
        })
        .addTo(this.mapa);
      centralizar = true;
    } else {
      this.marcador.setLatLng(ll);
    }
    if (centralizar) this.mapa.setView(ll, 16);
  }
}
