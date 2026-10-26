import { Routes } from '@angular/router';

import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      {
        path: '',
        title: 'MedFlow',
        loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
      },
      {
        path: 'ubs',
        title: 'Unidades · MedFlow',
        loadComponent: () => import('./features/ubs/ubs-list.page').then((m) => m.UbsListPage),
      },
      {
        path: 'ubs/:id',
        title: 'Unidade · MedFlow',
        loadComponent: () => import('./features/ubs/ubs-detail.page').then((m) => m.UbsDetailPage),
      },
      {
        path: 'medicamentos',
        title: 'Medicamentos · MedFlow',
        loadComponent: () =>
          import('./features/medicamentos/medicamento-search.page').then(
            (m) => m.MedicamentoSearchPage,
          ),
      },
      {
        path: 'medicamentos/:id',
        title: 'Medicamento · MedFlow',
        loadComponent: () =>
          import('./features/medicamentos/medicamento-detail.page').then(
            (m) => m.MedicamentoDetailPage,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
