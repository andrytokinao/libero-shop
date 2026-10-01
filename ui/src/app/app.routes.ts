import { Routes } from '@angular/router';
import { anonymousOnlyGuard, homeRedirectGuard, roleGuard } from './core/guards/auth.guard';
import { serverConfiguredGuard } from './core/guards/server.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [serverConfiguredGuard, homeRedirectGuard],
    children: [],
  },

  {
    path: 'login',
    title: 'Connexion',
    canActivate: [serverConfiguredGuard, anonymousOnlyGuard],
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
  },

  // Which server the mobile app talks to. Open to everyone, signed in or not: it is the first
  // screen of a fresh install, and the one to reach when the server's address has moved.
  {
    path: 'serveur',
    title: 'Serveur',
    loadComponent: () =>
      import('./features/settings/server-setup.component').then((m) => m.ServerSetupComponent),
  },

  // Getting the Android app. Open to everyone too: a phone is equipped before its owner has an
  // account, and the link to this page is what gets passed around.
  {
    path: 'application-mobile',
    title: 'Application mobile',
    loadComponent: () =>
      import('./features/mobile/mobile-download.component').then((m) => m.MobileDownloadComponent),
  },

  // A table's QR code. Open to everyone, signed in or not: it is the customer's page, and the
  // token in the link is all the server asks for.
  {
    path: 'commander/:token',
    title: 'Commander',
    loadComponent: () =>
      import('./features/public/table-order.component').then((m) => m.TableOrderComponent),
  },

  // ------------------------------------------------------------ cash desk
  {
    path: 'caisse',
    data: { segment: 'caisse' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      // Phone: the module's screens as cards, opened from the drawer. One per module.
      {
        path: 'menu',
        title: 'Caisse',
        loadComponent: () =>
          import('./shared/components/module-menu.component').then((m) => m.ModuleMenuComponent),
      },
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
        path: 'a-encaisser',
        title: 'À encaisser',
        loadComponent: () =>
          import('./features/cashier/to-collect.component').then((m) => m.ToCollectComponent),
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
        title: 'Versements reçus',
        loadComponent: () =>
          import('./features/cashier/remittance-inbox.component').then(
            (m) => m.RemittanceInboxComponent,
          ),
      },
    ],
  },

  // ----------------------------------------------------------- order taker
  {
    path: 'commandes',
    data: { segment: 'commandes' },
    canActivate: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'nouvelle' },
      {
        path: 'menu',
        title: 'Commandes',
        loadComponent: () =>
          import('./shared/components/module-menu.component').then((m) => m.ModuleMenuComponent),
      },
      {
        path: 'nouvelle',
        title: 'Nouvelle commande',
        loadComponent: () =>
          import('./features/orders/order-taking.component').then((m) => m.OrderTakingComponent),
      },
      {
        path: 'mes-commandes',
        title: 'Mes commandes',
        loadComponent: () =>
          import('./features/orders/my-orders.component').then((m) => m.MyOrdersComponent),
      },
      // The same screen as the depot's: cash in hand, and the slips brought to the till.
      {
        path: 'argent',
        title: 'Argent à remettre',
        loadComponent: () =>
          import('./features/depot/depot-cash.component').then((m) => m.DepotCashComponent),
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
        path: 'menu',
        title: 'Dépôt',
        loadComponent: () =>
          import('./shared/components/module-menu.component').then((m) => m.ModuleMenuComponent),
      },
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
        path: 'menu',
        title: 'Stock',
        loadComponent: () =>
          import('./shared/components/module-menu.component').then((m) => m.ModuleMenuComponent),
      },
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
        path: 'categories',
        title: 'Catégories',
        loadComponent: () =>
          import('./features/depot-manager/categories.component').then((m) => m.CategoriesComponent),
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
        path: 'menu',
        title: 'Admin',
        loadComponent: () =>
          import('./shared/components/module-menu.component').then((m) => m.ModuleMenuComponent),
      },
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
      // Moved out of admin when it opened to everyone; kept for bookmarks.
      { path: 'application-mobile', redirectTo: '/application-mobile' },
      {
        path: 'marges',
        title: 'Bénéfices & stock',
        loadComponent: () =>
          import('./features/admin/margin.component').then((m) => m.MarginComponent),
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
      {
        path: 'configuration',
        title: 'Configuration de la boutique',
        loadComponent: () =>
          import('./features/admin/shop-settings.component').then((m) => m.ShopSettingsComponent),
      },
      {
        path: 'sauvegardes',
        title: 'Sauvegardes',
        loadComponent: () =>
          import('./features/admin/backups.component').then((m) => m.BackupsComponent),
      },
      {
        path: 'tables',
        title: 'Tables & QR codes',
        loadComponent: () =>
          import('./features/admin/dining-tables.component').then((m) => m.DiningTablesComponent),
      },
      {
        path: 'licence',
        title: 'Licence',
        loadComponent: () =>
          import('./features/admin/license.component').then((m) => m.LicenseComponent),
      },
    ],
  },

  { path: '**', canActivate: [homeRedirectGuard], children: [] },
];
