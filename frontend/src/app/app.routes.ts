import { Routes } from '@angular/router';

import { exigirBoasVindas } from './core/guards/boas-vindas.guard';
import { Shell } from './layout/shell';

const emBreve = () => import('./features/em-breve/em-breve.page').then((m) => m.EmBrevePage);

export const routes: Routes = [
  {
    // Área da farmácia (gestão), com menu próprio. TODO(L6): restringir ao papel GESTOR.
    path: 'gestao',
    loadComponent: () => import('./layout/gestao-shell').then((m) => m.GestaoShell),
    children: [
      {
        path: '',
        title: 'Painel da gestão · UBS Conecta',
        loadComponent: () =>
          import('./features/gestao/painel-gestao.page').then((m) => m.PainelGestaoPage),
      },
      {
        path: 'estoque',
        title: 'Estoque · Gestão · UBS Conecta',
        data: { titulo: 'Estoque' },
        loadComponent: emBreve,
      },
      {
        path: 'remessas',
        title: 'Remessas · Gestão · UBS Conecta',
        data: { titulo: 'Remessas' },
        loadComponent: emBreve,
      },
      {
        path: 'demanda',
        title: 'Demanda dos pacientes · Gestão · UBS Conecta',
        data: { titulo: 'Demanda dos pacientes' },
        loadComponent: emBreve,
      },
    ],
  },
  {
    // Primeira visita do cidadão: tela cheia, sem o menu.
    path: 'boas-vindas',
    title: 'Boas-vindas · UBS Conecta',
    loadComponent: () =>
      import('./features/boas-vindas/boas-vindas.page').then((m) => m.BoasVindasPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [exigirBoasVindas],
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
        loadComponent: () => import('./features/lista/lista.page').then((m) => m.ListaPage),
      },
      {
        path: 'avisos',
        title: 'Avisos · UBS Conecta',
        loadComponent: () => import('./features/avisos/avisos.page').then((m) => m.AvisosPage),
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
