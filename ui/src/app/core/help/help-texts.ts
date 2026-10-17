/** The windows that carry a "?": one help text each. */
export type HelpTopic =
  | 'product-create'
  | 'product-config'
  | 'stock-entry'
  | 'stock-adjust'
  | 'delivery'
  | 'rayon-count'
  | 'pay'
  | 'hand-over'
  | 'cancel-order'
  | 'import'
  | 'unit-models'
  | 'supplier'
  | 'user'
  | 'checkout';

/** Two or three sentences, then an example from the shop. */
export interface HelpText {
  readonly text: string;
  readonly example: string;
}

/**
 * What each window is for, in the shop's words: kept here, together, so the same idea is said the
 * same way everywhere and a text is corrected once.
 */
export const HELP_TEXTS: Record<HelpTopic, HelpText> = {
  'product-create': {
    text:
      "Le nom, le prix de vente et l'unité suffisent pour vendre. Le reste (rayon, code-barres, " +
      "prix d'entrée, stock de départ) peut se compléter plus tard depuis le menu « ⋮ » du produit.",
    example: 'Riz 5 kg, vendu 12 500 Ar le sac, unité « sac ».',
  },
  'product-config': {
    text:
      "Le prix de vente est ce que paie le client ; le prix d'entrée est ce que vous payez au " +
      "fournisseur. La différence est votre bénéfice. Un produit peut se vendre en plusieurs unités, " +
      'chacune avec son prix et son code-barres.',
    example: 'Lait en poudre : 9 500 Ar la boîte, 110 000 Ar le carton de 12.',
  },
  'stock-entry': {
    text:
      "Pour la marchandise qui arrive. Le prix d'achat se tape dans l'unité reçue ; il sert à " +
      'calculer le bénéfice des ventes suivantes. Une erreur se rattrape avec « Annuler » dans le message.',
    example: '20 sacs de Riz 5 kg à 9 400 Ar le sac, livrés par Grossiste Analakely.',
  },
  'stock-adjust': {
    text:
      'Pour ce qui sort du stock sans être vendu (casse, perte, périmé), ou pour un comptage : ' +
      "« Inventaire » prend ce qui est sur l'étagère, et le stock se corrige de la différence.",
    example: '2 cartons de jus tombés : Casse, 2, en carton.',
  },
  delivery: {
    text:
      'Un bon de livraison entier en une fois : un fournisseur, plusieurs produits. Tout est ' +
      'enregistré ensemble, ou rien si une ligne est refusée.',
    example: 'Distri Lait SARL livre 2 cartons de lait et 10 yaourts.',
  },
  'rayon-count': {
    text:
      "Comptez l'étagère produit par produit. Un produit laissé vide n'est pas touché. Les écarts " +
      "sont montrés avant d'être enregistrés.",
    example: "Rayon Boissons : 3 bouteilles d'eau trouvées au lieu de 4.",
  },
  pay: {
    text:
      "Choisissez comment le client paie. En espèces, tapez ce qu'il donne : la monnaie à rendre " +
      "s'affiche.",
    example: 'Facture de 8 400 Ar, le client donne 10 000 Ar : rendre 1 600 Ar.',
  },
  'hand-over': {
    text:
      'Remettez la commande au client après avoir vérifié les articles. Si elle n’est pas payée, ' +
      'dites comment le client paie maintenant.',
    example: 'F-1033 : 4 articles, payée à la caisse, remise au comptoir.',
  },
  'cancel-order': {
    text:
      'Une commande annulée reste dans les factures, marquée annulée, avec le motif. Les articles ' +
      'non remis reviennent dans le stock.',
    example: 'Client parti avant la préparation : « client parti ».',
  },
  import: {
    text:
      "Préparez le fichier Excel avec le modèle proposé. L'aperçu montre ce qui sera créé ou " +
      "modifié avant d'enregistrer ; rien n'est écrit tant que vous ne confirmez pas.",
    example: 'Une ligne par produit : nom, prix, unité, rayon, code-barres.',
  },
  'unit-models': {
    text:
      'Un modèle regroupe des unités qui vont ensemble, avec leurs contenances. Il se coche ensuite ' +
      'en un clic sur chaque produit concerné.',
    example: 'Poids : g, kg (1 000 g), sac de 50 kg.',
  },
  supplier: {
    text:
      'Le nom suffit. Le fournisseur est ensuite proposé à chaque réception, et la page Fournisseurs ' +
      'compare ses prix à ceux des autres.',
    example: 'Grossiste Analakely, 034 12 345 67.',
  },
  user: {
    text:
      "Un compte par personne : chacun signe ses ventes et son argent. Les rôles décident des écrans " +
      "qu'elle voit ; une personne peut en avoir plusieurs.",
    example: 'Fatima : Caisse. Nadia : Stock et Dépôt.',
  },
  checkout: {
    text:
      "Vérifiez le total, choisissez le paiement, puis validez (F10). Une vente non payée part au " +
      "dépôt ou à « À encaisser » selon la configuration.",
    example: 'Panier de 12 500 Ar payé en espèces avec 20 000 Ar : rendre 7 500 Ar.',
  },
};
