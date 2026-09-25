import { RoleApp } from '../models';

export interface MenuItem {
  /** Glyph shown in the sidebar — kept text-only, no icon font needed. */
  icon: string;
  label: string;
  /** Absolute router path. */
  path: string;
}

export interface RoleNavigation {
  /** Route segment owned by the role, used by the role guard. */
  segment: string;
  items: MenuItem[];
}

export const ROLE_NAVIGATION: Record<RoleApp, RoleNavigation> = {
  [RoleApp.CASHIER]: {
    segment: 'caisse',
    items: [
      { icon: '◧', label: 'Tableau de bord', path: '/caisse/tableau-de-bord' },
      { icon: '＋', label: 'Nouvelle vente', path: '/caisse/nouvelle-vente' },
      { icon: '≣', label: 'Ventes du jour', path: '/caisse/ventes-du-jour' },
      { icon: '▤', label: 'Factures', path: '/caisse/factures' },
      { icon: '⇤', label: 'Versements dépôt', path: '/caisse/versements' },
    ],
  },
  [RoleApp.DEPOT_AGENT]: {
    segment: 'depot',
    items: [
      { icon: '◧', label: 'Tableau de bord', path: '/depot/tableau-de-bord' },
      { icon: '⇥', label: 'Remise de commande', path: '/depot/remise' },
      { icon: '$', label: 'Caisse dépôt', path: '/depot/caisse' },
      { icon: '≣', label: 'Historique des remises', path: '/depot/historique' },
    ],
  },
  [RoleApp.DEPOT_MANAGER]: {
    segment: 'gestion-depot',
    items: [
      { icon: '◧', label: 'Tableau de bord', path: '/gestion-depot/tableau-de-bord' },
      { icon: '▢', label: 'Stock', path: '/gestion-depot/stock' },
      { icon: '⇩', label: 'Approvisionnement', path: '/gestion-depot/approvisionnement' },
      { icon: '⇧', label: 'Sorties', path: '/gestion-depot/sorties' },
      { icon: '⛟', label: 'Fournisseurs', path: '/gestion-depot/fournisseurs' },
    ],
  },
  [RoleApp.SUPER_ADMIN]: {
    segment: 'admin',
    items: [
      { icon: '◧', label: "Vue d'ensemble", path: '/admin/vue-ensemble' },
      { icon: '↗', label: "Chiffre d'affaires", path: '/admin/chiffre-affaires' },
      { icon: '▢', label: 'Stock global', path: '/admin/stock-global' },
      { icon: '▤', label: 'Toutes les factures', path: '/admin/factures' },
      { icon: '⚉', label: 'Utilisateurs', path: '/admin/utilisateurs' },
    ],
  },
};

/** Landing page of a role — the first entry of its menu. */
export function homePathOf(role: RoleApp): string {
  return ROLE_NAVIGATION[role].items[0].path;
}
