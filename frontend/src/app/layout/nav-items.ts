export interface NavItem {
  path: string;
  label: string;
  exact: boolean;
  /** Path SVG (viewBox 24x24, traço). */
  icon: string;
}

/** Abas principais, na ordem do Figma. */
export const NAV_ITEMS: readonly NavItem[] = [
  {
    path: '/',
    label: 'Início',
    exact: true,
    icon: 'M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  },
  {
    path: '/mapa',
    label: 'Mapa',
    exact: false,
    icon: 'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12zM12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  },
  {
    path: '/medicamentos',
    label: 'Buscar',
    exact: false,
    icon: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
  },
  {
    path: '/lista',
    label: 'Minha lista',
    exact: false,
    icon: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
  },
  {
    path: '/perfil',
    label: 'Perfil',
    exact: false,
    icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  },
];
