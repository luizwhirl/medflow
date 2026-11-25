import { primeiraProcuraDoDia } from './buscas-contadas';

describe('primeiraProcuraDoDia', () => {
  beforeEach(() => localStorage.removeItem('medflow.buscas-contadas'));
  afterEach(() => localStorage.removeItem('medflow.buscas-contadas'));

  const manha = new Date('2026-11-25T12:00:00Z'); // 9h em Maceió

  it('conta o mesmo remédio só uma vez por dia, e outros remédios à parte', () => {
    expect(primeiraProcuraDoDia(7, manha)).toBeTrue();
    expect(primeiraProcuraDoDia(7, manha)).toBeFalse();
    expect(primeiraProcuraDoDia(8, manha)).toBeTrue();
  });

  it('volta a contar no dia seguinte, no fuso de Maceió', () => {
    expect(primeiraProcuraDoDia(7, manha)).toBeTrue();
    // 02:30 UTC do dia 26 ainda é 23:30 do dia 25 em Maceió
    expect(primeiraProcuraDoDia(7, new Date('2026-11-26T02:30:00Z'))).toBeFalse();
    expect(primeiraProcuraDoDia(7, new Date('2026-11-26T03:30:00Z'))).toBeTrue();
  });

  it('com valor corrompido no navegador, conta normalmente', () => {
    localStorage.setItem('medflow.buscas-contadas', '{quebrado');
    expect(primeiraProcuraDoDia(7, manha)).toBeTrue();
  });
});
