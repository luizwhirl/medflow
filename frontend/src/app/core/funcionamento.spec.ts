import { horaCurta, situacaoFuncionamento } from './funcionamento';

describe('situacaoFuncionamento', () => {
  // Sexta, 20/11/2026, 10:00 em Maceió (UTC-3)
  const agora = new Date('2026-11-20T10:00:00-03:00').getTime();
  const fechaEm = (iso: string) => ({
    abertaAgora: true,
    fechaAs: iso.slice(11, 16),
    fechaEm: iso,
  });

  it('não mostra nada quando a UBS não tem horário cadastrado', () => {
    expect(situacaoFuncionamento({ abertaAgora: null }, agora)).toBeNull();
    expect(situacaoFuncionamento({}, agora)).toBeNull();
  });

  it('fora do horário (ou no fim de semana) diz que está fechada', () => {
    expect(situacaoFuncionamento({ abertaAgora: false, fechaAs: null }, agora)).toEqual({
      texto: 'Fechada agora',
      tom: 'fechada',
    });
  });

  it('dentro do horário diz até quando fica aberta', () => {
    expect(situacaoFuncionamento(fechaEm('2026-11-20T17:00:00-03:00'), agora)).toEqual({
      texto: 'Aberta até 17h',
      tom: 'aberta',
    });
  });

  it('com até 60 minutos para fechar, avisa a hora de fechar', () => {
    expect(situacaoFuncionamento(fechaEm('2026-11-20T11:00:00-03:00'), agora)).toEqual({
      texto: 'Fecha às 11h',
      tom: 'fechando',
    });
    expect(situacaoFuncionamento(fechaEm('2026-11-20T11:01:00-03:00'), agora)?.texto).toBe(
      'Aberta até 11h01',
    );
  });

  it('turno que vira a noite usa a data completa do fechamento', () => {
    // Plantão de sexta, 19h às 7h: às 23:30 faltam 7 h 30 para fechar (no sábado)
    const noite = new Date('2026-11-20T23:30:00-03:00').getTime();
    expect(situacaoFuncionamento(fechaEm('2026-11-21T07:00:00-03:00'), noite)?.texto).toBe(
      'Aberta até 7h',
    );
  });

  it('aberta sem hora de fechar é uma unidade 24 horas', () => {
    expect(situacaoFuncionamento({ abertaAgora: true, fechaAs: null }, agora)).toEqual({
      texto: 'Aberta 24 horas',
      tom: 'aberta',
    });
  });

  it('formata a hora sem os minutos zerados', () => {
    expect(horaCurta('17:00')).toBe('17h');
    expect(horaCurta('07:30')).toBe('7h30');
    expect(horaCurta('00:00')).toBe('0h');
  });
});
