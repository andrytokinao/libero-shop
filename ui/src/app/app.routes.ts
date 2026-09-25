import { Routes } from '@angular/router';
import { anonymousOnlyGuard, homeRedirectGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },

  {
    path: 'login',
    title: 'Connexion',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
  },

  // ------------------------------------------------------------ cash desk
  {
    path: 'caisse',
    data: { segment: 'caisse' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord',
        loadComponent: () =>
          import('./features/cashier/cashier-dashboard.component').then(
            (m) => m.CashierDashboardComponent,
          ),
      },
      {
        path: 'nouvelle-vente',
        title: 'Nouvelle vente',
        loadComponent: () =>
          import('./features/cashier/new-sale.component').then((m) => m.NewSaleComponent),
      },
      {
        path: 'ventes-du-jour',
        title: 'Ventes du jour',
        loadComponent: () =>
          import('./features/cashier/daily-sales.component').then((m) => m.DailySalesComponent),
      },
      {
        path: 'factures',
        title: 'Mes factures',
        loadComponent: () =>
          import('./features/cashier/cashier-invoices.component').then(
            (m) => m.CashierInvoicesComponent,
          ),
      },
      {
        path: 'versements',
        title: 'Versements dépôt',
        loadComponent: () =>
          import('./features/cashier/remittance-inbox.component').then(
            (m) => m.RemittanceInboxComponent,
          ),
      },
    ],
  },

  // ----------------------------------------------------------- depot agent
  {
    path: 'depot',
    data: { segment: 'depot' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord',
        loadComponent: () =>
          import('./features/depot/depot-dashboard.component').then((m) => m.DepotDashboardComponent),
      },
      {
        path: 'remise',
        title: 'Remise de commande',
        loadComponent: () =>
          import('./features/depot/order-delivery.component').then((m) => m.OrderDeliveryComponent),
      },
      {
        path: 'caisse',
        title: 'Caisse dépôt',
        loadComponent: () =>
          import('./features/depot/depot-cash.component').then((m) => m.DepotCashComponent),
      },
      {
        path: 'historique',
        title: 'Historique des remises',
        loadComponent: () =>
          import('./features/depot/delivery-history.component').then(
            (m) => m.DeliveryHistoryComponent,
          ),
      },
    ],
  },

  // --------------------------------------------------------- depot manager
  {
    path: 'gestion-depot',
    data: { segment: 'gestion-depot' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord',
        loadComponent: () =>
          import('./features/depot-manager/manager-dashboard.component').then(
            (m) => m.ManagerDashboardComponent,
          ),
      },
      {
        path: 'stock',
        title: 'Stock',
        loadComponent: () =>
          import('./features/depot-manager/stock.component').then((m) => m.StockComponent),
      },
      {
        path: 'approvisionnement',
        title: 'Approvisionnement',
        loadComponent: () =>
          import('./features/depot-manager/supply.component').then((m) => m.SupplyComponent),
      },
      {
        path: 'sorties',
        title: 'Sorties',
        loadComponent: () =>
          import('./features/depot-manager/stock-outputs.component').then(
            (m) => m.StockOutputsComponent,
          ),
      },
      {
        path: 'fournisseurs',
        title: 'Fournisseurs',
        loadComponent: () =>
          import('./features/depot-manager/suppliers.component').then((m) => m.SuppliersComponent),
      },
    ],
  },

  // ----------------------------------------------------------- super admin
  {
    path: 'admin',
    data: { segment: 'admin' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'vue-ensemble' },
      {
        path: 'vue-ensemble',
        title: "Vue d'ensemble",
        loadComponent: () =>
          import('./features/admin/admin-overview.component').then((m) => m.AdminOverviewComponent),
      },
      {
        path: 'chiffre-affaires',
        title: "Chiffre d'affaires",
        loadComponent: () =>
          import('./features/admin/revenue.component').then((m) => m.RevenueComponent),
      },
      {
        path: 'stock-global',
        title: 'Stock global',
        loadComponent: () =>
          import('./features/admin/global-stock.component').then((m) => m.GlobalStockComponent),
      },
      {
        path: 'factures',
        title: 'Toutes les factures',
        loadComponent: () =>
          import('./features/admin/all-invoices.component').then((m) => m.AllInvoicesComponent),
      },
      {
        path: 'utilisateurs',
        title: 'Utilisateurs',
        loadComponent: () =>
          import('./features/admin/users.component').then((m) => m.UsersComponent),
      },
    ],
  },

  { path: '**', canActivate: [homeRedirectGuard], children: [] },
];
