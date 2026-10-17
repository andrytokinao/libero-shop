import { FirstSteps } from '../models';

/** Who sets the shop up: the administrator, or the depot manager who has no access to accounts. */
export type SetupRole = 'admin' | 'manager';

/** One box of the "Premiers pas" card, and where to go to tick it. */
export interface FirstStep {
  readonly key: keyof FirstSteps;
  readonly label: string;
  readonly done: boolean;
  /** The page that does it; none when the step opens a dialog here or is someone else's job. */
  readonly link?: string;
  /** Opens "Nouveau produit" on the dashboard itself. */
  readonly createsProduct?: boolean;
  /** Said instead of a link when this account cannot do the step itself. */
  readonly note?: string;
}

/** The six steps of a new shop, in the order they are usually done, with where each is done. */
export function firstStepsOf(progress: FirstSteps, role: SetupRole): FirstStep[] {
  const base = role === 'admin' ? '/admin' : '/gestion-depot';
  return [
    { key: 'product', label: 'Créer un premier produit', done: progress.product, createsProduct: true },
    {
      key: 'purchasePrice',
      label: "Lui donner un prix d'entrée",
      done: progress.purchasePrice,
      link: `${base}/prix-et-unites`,
    },
    {
      key: 'barcode',
      label: 'Ajouter un code-barres',
      done: progress.barcode,
      link: `${base}/prix-et-unites`,
    },
    {
      key: 'stock',
      label: 'Ajouter du stock',
      done: progress.stock,
      link: role === 'admin' ? '/admin/stock-global' : '/gestion-depot/approvisionnement',
    },
    {
      key: 'cashier',
      label: 'Créer un compte caissier',
      done: progress.cashier,
      ...(role === 'admin' ? { link: '/admin/utilisateurs' } : { note: "par l'administrateur" }),
    },
    { key: 'sale', label: 'Faire une première vente', done: progress.sale, note: 'à la caisse' },
  ];
}

/** "Votre boutique est prête à 50 %": the share of the steps done, rounded down. */
export function readiness(steps: readonly FirstStep[]): number {
  return Math.floor((100 * steps.filter((step) => step.done).length) / steps.length);
}
