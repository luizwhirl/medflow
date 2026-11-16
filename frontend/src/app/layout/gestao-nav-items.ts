export interface IconeFigma {
  src: string;
  /** Tamanho original do SVG exportado do Figma (px). */
  largura: number;
  altura: number;
}

export interface GestaoNavItem {
  path: string;
  /** Rótulo do menu completo (desktop). */
  label: string;
  /** Rótulo do trilho (tablet) e da barra do celular. */
  curto: string;
  exact: boolean;
  icone: IconeFigma;
}

/** Menu da farmácia, na ordem do frame "Desktop · Painel da gestão". */
export const GESTAO_NAV_ITEMS: readonly GestaoNavItem[] = [
  {
    path: '/gestao',
    label: 'Visão geral',
    curto: 'Visão geral',
    exact: true,
    icone: { src: 'icons/gestao/chart.svg', largura: 18.6667, altura: 15.3333 },
  },
  {
    path: '/gestao/estoque',
    label: 'Estoque',
    curto: 'Estoque',
    exact: false,
    icone: { src: 'icons/gestao/pill.svg', largura: 15.3206, altura: 15.3206 },
  },
  {
    path: '/gestao/remessas',
    label: 'Remessas',
    curto: 'Remessas',
    exact: false,
    icone: { src: 'icons/gestao/truck.svg', largura: 17, altura: 13.6667 },
  },
  {
    path: '/gestao/demanda',
    label: 'Demanda dos pacientes',
    curto: 'Demanda',
    exact: false,
    icone: { src: 'icons/gestao/user.svg', largura: 15.3333, altura: 16.1667 },
  },
];

export const ICONE_MARCA_GESTAO: IconeFigma = {
  src: 'icons/gestao/pin.svg',
  largura: 13.6667,
  altura: 17.8333,
};
