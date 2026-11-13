import { Routes } from '@angular/router';

import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      {
        path: '',
        title: 'UBS Conecta',
        loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
      },
      {
        path: 'ubs/:id',
        title: 'Unidade · UBS Conecta',
        loadComponent: () => import('./features/ubs/ubs-detail.page').then((m) => m.UbsDetailPage),
      },
      {
        path: 'medicamentos',
        title: 'Medicamentos · UBS Conecta',
        loadComponent: () =>
          import('./features/medicamentos/medicamento-search.page').then(
            (m) => m.MedicamentoSearchPage,
          ),
      },
      {
        path: 'mapa',
        title: 'Mapa · UBS Conecta',
        loadComponent: () => import('./features/ubs/ubs-map.page').then((m) => m.UbsMapPage),
      },
      {
        path: 'lista',
        title: 'Minha lista · UBS Conecta',
        data: { titulo: 'Minha lista' },
        loadComponent: () => import('./features/em-breve/em-breve.page').then((m) => m.EmBrevePage),
      },
      {
        path: 'avisos',
        title: 'Avisos · UBS Conecta',
        data: { titulo: 'Avisos' },
        loadComponent: () => import('./features/em-breve/em-breve.page').then((m) => m.EmBrevePage),
      },
      {
        path: 'perfil',
        title: 'Perfil · UBS Conecta',
        data: { titulo: 'Perfil e modo offline' },
        loadComponent: () => import('./features/em-breve/em-breve.page').then((m) => m.EmBrevePage),
      },
      {
        path: 'medicamentos/:id',
        title: 'Medicamento · UBS Conecta',
        loadComponent: () =>
          import('./features/medicamentos/medicamento-detail.page').then(
            (m) => m.MedicamentoDetailPage,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
