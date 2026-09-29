import { ROLE_PRECEDENCE, RoleApp } from '../models';

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
  /** Short name of the job, shown as a heading when an account holds several roles. */
  label: string;
  items: MenuItem[];
}

export const ROLE_NAVIGATION: Record<RoleApp, RoleNavigation> = {
  [RoleApp.CASHIER]: {
    segment: 'caisse',
    label: 'Caisse',
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
    label: 'Dépôt',
    items: [
      { icon: '◧', label: 'Tableau de bord', path: '/depot/tableau-de-bord' },
      { icon: '⇥', label: 'Remise de commande', path: '/depot/remise' },
      { icon: '$', label: 'Caisse dépôt', path: '/depot/caisse' },
      { icon: '≣', label: 'Historique des remises', path: '/depot/historique' },
    ],
  },
  [RoleApp.DEPOT_MANAGER]: {
    segment: 'gestion-depot',
    label: 'Stock',
    items: [
      { icon: '◧', label: 'Tableau de bord', path: '/gestion-depot/tableau-de-bord' },
      { icon: '▢', label: 'Stock', path: '/gestion-depot/stock' },
      { icon: '⊞', label: 'Catégories', path: '/gestion-depot/categories' },
      { icon: '⇩', label: 'Approvisionnement', path: '/gestion-depot/approvisionnement' },
      { icon: '⇧', label: 'Sorties', path: '/gestion-depot/sorties' },
      { icon: '⛟', label: 'Fournisseurs', path: '/gestion-depot/fournisseurs' },
    ],
  },
  [RoleApp.SUPER_ADMIN]: {
    segment: 'admin',
    label: 'Admin',
    items: [
      { icon: '◧', label: "Vue d'ensemble", path: '/admin/vue-ensemble' },
      { icon: '↗', label: "Chiffre d'affaires", path: '/admin/chiffre-affaires' },
      { icon: '%', label: 'Marges', path: '/admin/marges' },
      { icon: '▢', label: 'Stock global', path: '/admin/stock-global' },
      { icon: '▤', label: 'Toutes les factures', path: '/admin/factures' },
      { icon: '⚉', label: 'Utilisateurs', path: '/admin/utilisateurs' },
      // No 'Licence' entry: the page stays reachable at /admin/licence, and the licence
      // bar links to it once expiry is within LICENSE_BANNER_DAYS.
    ],
  },
};

/**
 * The sidebar of an account: one section per role it holds.
 *
 * <p>A single-role account gets exactly the menu it had before, so nothing changes for a
 * depot that splits the duties between four people. An account that cumulates them gets the
 * sections one after another rather than a merged list, because "Tableau de bord" appears in
 * three of them and only the heading tells them apart.
 */
export function navigationFor(roles: readonly RoleApp[]): RoleNavigation[] {
  return ROLE_PRECEDENCE.filter((role) => roles.includes(role)).map((role) => ROLE_NAVIGATION[role]);
}

/** True when one of the roles owns that route segment — what the role guard asks. */
export function ownsSegment(roles: readonly RoleApp[], segment: string): boolean {
  return roles.some((role) => ROLE_NAVIGATION[role].segment === segment);
}
