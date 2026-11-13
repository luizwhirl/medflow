export interface NavItem {
  path: string;
  /** Rótulo do menu lateral completo (desktop). */
  label: string;
  /** Rótulo curto do trilho (tablet) e da barra inferior (celular). */
  curto: string;
  exact: boolean;
  /** Path SVG (viewBox 24x24, traço). */
  icon: string;
}

/** Abas principais, na ordem do Figma. */
export const NAV_ITEMS: readonly NavItem[] = [
  {
    path: '/',
    label: 'Início',
    curto: 'Início',
    exact: true,
    icon: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  },
  {
    path: '/mapa',
    label: 'Mapa de UBS',
    curto: 'Mapa',
    exact: false,
    icon: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14',
  },
  {
    path: '/medicamentos',
    label: 'Buscar medicamento',
    curto: 'Buscar',
    exact: false,
    icon: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
  },
  {
    path: '/lista',
    label: 'Minha lista',
    curto: 'Minha lista',
    exact: false,
    icon: 'M6 3h12v18l-6-4-6 4z',
  },
  {
    path: '/avisos',
    label: 'Avisos',
    curto: 'Avisos',
    exact: false,
    icon: 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0',
  },
  {
    path: '/perfil',
    label: 'Perfil e modo offline',
    curto: 'Perfil',
    exact: false,
    icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  },
];

/** Ícone da marca (pin de localização). */
export const ICONE_MARCA =
  'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12zM12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z';
