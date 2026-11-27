import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { LocalizacaoUbs } from '../../core/models/gestao.model';
import { GestaoService } from '../../core/services/gestao.service';
import { LocalizacaoUbsCard } from './localizacao-ubs';

const BAIRRO: LocalizacaoUbs = {
  ubsId: 7,
  nome: 'US Aliomar Almeida Lins',
  endereco: 'Lot Bela Vista II, S/N',
  bairro: 'Benedito Bentes II',
  latitude: -9.55,
  longitude: -35.72,
  origem: 'BAIRRO',
  aproximada: true,
};

describe('LocalizacaoUbsCard', () => {
  let corrigir: jasmine.Spy;

  function montar(local: LocalizacaoUbs = BAIRRO): ComponentFixture<LocalizacaoUbsCard> {
    corrigir = jasmine.createSpy('corrigirLocalizacao');
    TestBed.configureTestingModule({
      providers: [
        {
          provide: GestaoService,
          useValue: { localizacao: () => of(local), corrigirLocalizacao: corrigir },
        },
      ],
    });
    const f = TestBed.createComponent(LocalizacaoUbsCard);
    f.componentRef.setInput('ubsId', 7);
    f.detectChanges();
    return f;
  }

  const el = (f: ComponentFixture<LocalizacaoUbsCard>) => f.nativeElement as HTMLElement;
  const salvar = (f: ComponentFixture<LocalizacaoUbsCard>) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes('Salvar'))!;
  /** Simula o gestor arrastando o pino. */
  const moverPino = (
    f: ComponentFixture<LocalizacaoUbsCard>,
    latitude: number,
    longitude: number,
  ) => {
    (
      f.componentInstance as unknown as { mover(p: { latitude: number; longitude: number }): void }
    ).mover({
      latitude,
      longitude,
    });
    f.detectChanges();
  };

  it('explica a origem do pino (aproximada no centro do bairro)', () => {
    const f = montar();
    expect(el(f).querySelector('[data-teste="origem"]')?.textContent).toContain(
      'Localização aproximada (centro do bairro)',
    );
    expect(el(f).textContent).toContain('Lot Bela Vista II, S/N · Benedito Bentes II');
  });

  it('pino sem localização confiável avisa que a UBS não aparece para o cidadão', () => {
    const f = montar({ ...BAIRRO, origem: 'INVALIDA', aproximada: false });
    expect(el(f).querySelector('[data-teste="origem"]')?.textContent).toContain(
      'não aparece no mapa',
    );
  });

  it('"Salvar" só libera depois de mover o pino e grava a nova coordenada', () => {
    const f = montar();
    expect(salvar(f).disabled).toBeTrue();

    moverPino(f, -9.561, -35.731);
    expect(salvar(f).disabled).toBeFalse();

    corrigir.and.returnValue(
      of({ ...BAIRRO, latitude: -9.561, longitude: -35.731, origem: 'MANUAL', aproximada: false }),
    );
    salvar(f).click();
    f.detectChanges();

    expect(corrigir).toHaveBeenCalledWith(7, { latitude: -9.561, longitude: -35.731 });
    expect(el(f).querySelector('[data-teste="origem"]')?.textContent).toContain(
      'conferida pela gestão',
    );
    expect(el(f).textContent).toContain('Localização salva');
    expect(salvar(f).disabled).toBeTrue();
  });

  it('mostra o motivo quando a API recusa o ponto (fora do município)', () => {
    const f = montar();
    moverPino(f, -9.56, -34.86);
    corrigir.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { detail: 'O ponto fica fora do município da UBS' },
          }),
      ),
    );
    salvar(f).click();
    f.detectChanges();
    expect(el(f).querySelector('[role="status"]')?.textContent).toContain(
      'O ponto fica fora do município da UBS',
    );
  });
});
