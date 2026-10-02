import {
  DEFAULT_SHOP_SETTINGS,
  ROLE_PRECEDENCE,
  RoleApp,
  ShopFeatures,
  ShopSettings,
  Vocabulary,
  businessTypeInfo,
  hasCashTrail,
} from '../models';
import { IconName } from '../ui/icons';

export interface MenuItem {
  /** Drawn beside the label in the sidebar and on the module's cards. */
  icon: IconName;
  label: string;
  /** Absolute router path. */
  path: string;
  /** Hidden when the shop does not work this way: a screen for a step the shop does not take. */
  requires?: (features: ShopFeatures) => boolean;
  /** Named by the business type rather than by `label` — "Commandes à servir" in a restaurant. */
  wording?: keyof Vocabulary;
}

export interface RoleNavigation {
  /** Route segment owned by the role, used by the role guard. */
  segment: string;
  /** Short name of the job, shown as a heading when an account holds several roles. */
  label: string;
  /** The module itself, in the phone drawer that lists modules only. */
  icon: IconName;
  items: MenuItem[];
  requires?: (features: ShopFeatures) => boolean;
  wording?: keyof Vocabulary;
}

export const ROLE_NAVIGATION: Record<RoleApp, RoleNavigation> = {
  [RoleApp.CASHIER]: {
    segment: 'caisse',
    label: 'Caisse',
    icon: 'store',
    items: [
      { icon: 'dashboard', label: 'Tableau de bord', path: '/caisse/tableau-de-bord' },
      { icon: 'cart', label: 'Nouvelle vente', path: '/caisse/nouvelle-vente' },
      { icon: 'cash', label: 'À encaisser', path: '/caisse/a-encaisser' },
      { icon: 'list', label: 'Ventes du jour', path: '/caisse/ventes-du-jour' },
      { icon: 'invoice', label: 'Factures', path: '/caisse/factures' },
      { icon: 'inbox', label: 'Versements reçus', path: '/caisse/versements', requires: hasCashTrail },
    ],
  },
  [RoleApp.ORDER_TAKER]: {
    segment: 'commandes',
    label: 'Commandes',
    icon: 'clipboard',
    items: [
      { icon: 'plus', label: 'Nouvelle commande', path: '/commandes/nouvelle' },
      { icon: 'list', label: 'Mes commandes', path: '/commandes/mes-commandes' },
      {
        icon: 'wallet',
        label: 'Argent à remettre',
        path: '/commandes/argent',
        requires: (f) => f.orderTakerCollects,
      },
    ],
  },
  [RoleApp.DEPOT_AGENT]: {
    segment: 'depot',
    label: 'Dépôt',
    icon: 'package',
    requires: (f) => f.separateDelivery,
    wording: 'depot',
    items: [
      { icon: 'dashboard', label: 'Tableau de bord', path: '/depot/tableau-de-bord' },
      { icon: 'handOver', label: 'Remise de commande', path: '/depot/remise', wording: 'handOver' },
      { icon: 'wallet', label: 'Caisse dépôt', path: '/depot/caisse', requires: (f) => f.payAtDepot },
      { icon: 'history', label: 'Historique des remises', path: '/depot/historique' },
    ],
  },
  [RoleApp.DEPOT_MANAGER]: {
    segment: 'gestion-depot',
    label: 'Stock',
    icon: 'warehouse',
    items: [
      { icon: 'dashboard', label: 'Tableau de bord', path: '/gestion-depot/tableau-de-bord' },
      { icon: 'warehouse', label: 'Stock', path: '/gestion-depot/stock' },
      { icon: 'layers', label: 'Catégories', path: '/gestion-depot/categories' },
      { icon: 'stockIn', label: 'Approvisionnement', path: '/gestion-depot/approvisionnement' },
      { icon: 'stockOut', label: 'Sorties', path: '/gestion-depot/sorties' },
      { icon: 'truck', label: 'Fournisseurs', path: '/gestion-depot/fournisseurs' },
    ],
  },
  [RoleApp.SUPER_ADMIN]: {
    segment: 'admin',
    label: 'Admin',
    icon: 'shield',
    items: [
      { icon: 'overview', label: "Vue d'ensemble", path: '/admin/vue-ensemble' },
      { icon: 'trendingUp', label: "Chiffre d'affaires", path: '/admin/chiffre-affaires' },
      { icon: 'percent', label: 'Bénéfices & stock', path: '/admin/marges' },
      { icon: 'warehouse', label: 'Stock global', path: '/admin/stock-global' },
      { icon: 'invoice', label: 'Toutes les factures', path: '/admin/factures' },
      { icon: 'users', label: 'Utilisateurs', path: '/admin/utilisateurs' },
      { icon: 'settings', label: 'Configuration', path: '/admin/configuration' },
      { icon: 'database', label: 'Sauvegardes', path: '/admin/sauvegardes' },
      {
        icon: 'qrCode',
        label: 'Tables & QR codes',
        path: '/admin/tables',
        requires: (f) => f.onlineOrdering,
      },
      // No 'Licence' entry: the page stays reachable at /admin/licence, and the licence
      // bar links to it once expiry is within LICENSE_BANNER_DAYS.
    ],
  },
};

/**
 * The sidebar of an account: one section per role it holds, cut down to what the shop uses.
 *
 * <p>A single-role account gets exactly the menu it had before, so nothing changes for a
 * depot that splits the duties between four people. An account that cumulates them gets the
 * sections one after another rather than a merged list, because "Tableau de bord" appears in
 * three of them and only the heading tells them apart.
 *
 * <p>A screen the configuration switches off is left out — a counter has no depot queue, a
 * restaurant no depot cash — and the rest is named the way the business speaks. An account
 * whose only job the configuration switched off keeps its menu all the same, rather than an
 * empty one.
 */
export function navigationFor(
  roles: readonly RoleApp[],
  settings: ShopSettings = DEFAULT_SHOP_SETTINGS,
): RoleNavigation[] {
  const words = businessTypeInfo(settings.businessType).vocabulary;
  const enabled = (requires?: (features: ShopFeatures) => boolean) => !requires || requires(settings);
  const sections = ROLE_PRECEDENCE.filter((role) => roles.includes(role)).map(
    (role) => ROLE_NAVIGATION[role],
  );
  const kept = sections.filter((section) => enabled(section.requires));

  return (kept.length ? kept : sections).map((section) => ({
    ...section,
    label: section.wording ? words[section.wording] : section.label,
    items: section.items
      .filter((item) => enabled(item.requires))
      .map((item) => ({ ...item, label: item.wording ? words[item.wording] : item.label })),
  }));
}

/**
 * The module's own menu page: on a phone the drawer lists the modules only, and each one
 * opens this screen of cards rather than a long list of links.
 */
export function moduleMenuPath(section: RoleNavigation): string {
  return `/${section.segment}/menu`;
}

/** The module a URL belongs to, from its first segment — null outside the four modules. */
export function sectionOfUrl(url: string): RoleNavigation | null {
  const segment = url.split(/[/?#]/).find((part) => part !== '') ?? '';
  return Object.values(ROLE_NAVIGATION).find((section) => section.segment === segment) ?? null;
}

/** True when one of the roles owns that route segment — what the role guard asks. */
export function ownsSegment(roles: readonly RoleApp[], segment: string): boolean {
  return roles.some((role) => ROLE_NAVIGATION[role].segment === segment);
}
