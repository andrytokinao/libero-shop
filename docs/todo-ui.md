# Libero Shop — facilité d'utilisation (liste de travail)

Liste à traiter point par point, comme `TODO.md`. Le détail, les maquettes et les schémas sont
dans [`todo-ui.html`](todo-ui.html) : chaque repère `UI-xx` y a sa section.

Règle commune : on agit là où on voit la chose (menu « ⋮ » ou fenêtre), jamais par une autre page.
Réutiliser `app-action-menu`, `app-product-menu`, `app-product-dialogs`, `ProductRights` : étendre,
ne pas réécrire.

Priorités : **P1** gain immédiat, peu de code · **P2** important · **P3** confort.
« (serveur) » : demande aussi une modification du backend.

---

## P1

- [x] **UI-01** — « ⋮ » produit sur le tableau de bord du stock et la vue d'ensemble
  (`app-stock-table [manage]="true"` + `app-product-dialogs`), sur Prix et unités (à la place des
  boutons) et sur Bénéfices & stock.
- [x] **UI-21** — Code-barres inconnu : fenêtre « l'ajouter à un produit existant » / « créer le
  produit » (code prérempli) pour qui peut modifier le catalogue ; recherche par nom sinon.
- [x] **UI-30** — Sélecteur de produit partagé (recherche dès 2 lettres, douchette, caméra, stock
  affiché) à la place des listes déroulantes d'Approvisionnement et de Corrections.
- [x] **UI-32** — « ＋ Nouveau fournisseur… » en fin de liste des fournisseurs (mini-fenêtre nom,
  téléphone ; choisi d'office). (serveur : création de fournisseur)
- [x] **UI-50** — Numéro de facture cliquable (sorties, versements, historique du stock) →
  `invoice-detail-dialog`.
- [x] **UI-61** — Badge « prix d'achat inconnu » (Bénéfices & stock) → configuration du produit,
  onglet Prix.
- [x] **UI-80** — Listes vides : une phrase + le bouton de l'action (fournisseurs, produits, rayons…).
- [x] **UI-82** — Confirmations cohérentes : remise d'argent depuis « Argent en main » et « Tout
  remettre », désactivation d'utilisateur ; fenêtre de l'application au lieu de `window.confirm()`.
- [x] **UI-87** — Menu latéral d'un compte à plusieurs rôles (Caisse · Stock) : deux « Tableau de
  bord » indistincts et liste plus haute que l'écran ; nommer les entrées par section, sections
  repliables.
- [x] **UI-88** — Page de vente : une ressource demandée au chargement répond 404 (console) ;
  la retrouver et la corriger.

## P2

- [x] **UI-02** — Menu produit complet par familles (Stock / Configuration / Autres) : compter ce
  produit, nom et rayon, prix, unités et packaging, codes-barres, seuil d'alerte, dupliquer,
  retirer de la vente. Étendre `MenuAction` (`group`, `hint`, `disabledReason`).
  (serveur : seuil par produit, produit retiré de la vente)
- [x] **UI-10** — Fenêtre de configuration en onglets (Fiche, Prix, Unités et packaging,
  Codes-barres), ouverte sur l'onglet demandé (`initialTab`) ; pastilles d'unités toutes prêtes.
- [x] **UI-12** — Nouveau produit : nom, prix, unité d'abord, le reste replié ; message avec
  « Ajouter du stock » / « Codes-barres ».
- [x] **UI-20** — Onglet Codes-barres : une ligne par unité, bouton « Scanner », code déjà pris signalé.
- [x] **UI-22** — Douchette et caméra dans la recherche de Stock, Prix et unités, Corrections.
- [x] **UI-31** — « ＋ Réception » : fenêtre bon de livraison multi-produits, un fournisseur, un
  seul enregistrement. (serveur)
- [x] **UI-33** — « ⋮ » sur les réceptions et corrections passées : voir le produit, imprimer,
  annuler par mouvement inverse. (serveur)
- [x] **UI-34** — Page Fournisseurs : « ＋ Nouveau » et « ⋮ » (modifier, ses prix, nouvelle
  réception, ne plus proposer). (serveur)
- [x] **UI-40** — « ⋮ » par rayon : renommer, image, sous-rayon, voir/déplacer ses produits,
  compter, supprimer.
- [x] **UI-41** — Fenêtre « produits du rayon » : ajouter un produit au rayon, changer de rayon.
- [x] **UI-60** — Compteurs des tableaux de bord cliquables (liste filtrée).
- [x] **UI-70** — « ⋮ » des utilisateurs (modifier, rôles, mot de passe, désactiver confirmé).
- [x] **UI-72** — Configuration de la boutique : phrase « ce que ça change », résumé avant
  d'enregistrer.
- [x] **UI-81** — États chargement / vide / erreur distincts (factures, stock, fournisseurs,
  utilisateurs, tableaux de bord).
- [x] **UI-83** — Bouton « Annuler » dans le message (`toast.service.ts`) après une action
  réversible ; écrit le mouvement inverse. (serveur)
- [x] **UI-84** — Carte « Premiers pas » sur le tableau de bord d'une boutique neuve.

## P3

- [x] **UI-03** — Clic sur un produit (hors vente) : fiche rapide en lecture avec ses actions.
- [x] **UI-11** — Prix modifiable directement dans le tableau Prix et unités.
- [ ] **UI-35** — Correction de stock dans une autre unité (« 2 cartons »).
- [ ] **UI-36** — Inventaire d'un rayon avec résumé des écarts. (serveur)
- [ ] **UI-51** — « ⋮ » sur les lignes de factures (actions de `invoice-actions.ts`).
- [ ] **UI-52** — « ⋮ » sur une ligne du panier (unité, quantité, retirer).
- [ ] **UI-53** — Fiche courte d'un produit pendant la vente (appui long). Décision attendue.
- [ ] **UI-54** — Bouton « ⌨ » pour la légende des touches sous 980 px.
- [ ] **UI-71** — « ⋮ » des tables (renommer, copier, imprimer QR, nouveau code, supprimer). (serveur)
- [ ] **UI-85** — « ? » d'aide dans l'en-tête de chaque fenêtre.
- [ ] **UI-86** — Téléphone : appui long = « ⋮ », menu en panneau du bas sous 600 px.
- [ ] **UI-89** — Vente : rayons en barre compacte au-dessus de la grille (les tuiles de rayon
  occupent deux rangées avant le premier produit).
- [ ] **UI-90** — Message « article ajouté » de la vente : ne plus masquer le bas de la grille.
- [x] **UI-91** — Nouveau produit : dire ce qui manque quand « Créer le produit » est grisé ;
  aligner le champ Code-barres sur les autres.
- [ ] **UI-92** — Unité de base sans nom (« unité ») : la demander à la création, l'afficher
  partout comme « sac (unité de base) ».

## Décisions attendues

- Code inconnu à la caisse : prévenir le responsable de dépôt ? (UI-21)
- Plusieurs codes-barres par unité ? Aujourd'hui associer un code remplace l'ancien : les paquets
  encore en rayon avec l'ancien code deviennent inconnus. (serveur ; UI-20, UI-21)
- Prix d'achat saisi après coup : l'appliquer aussi aux ventes déjà faites sans coût ? Aujourd'hui le
  coût d'une vente est figé le jour de la vente (`unitCost`) ; le badge de Bénéfices reste donc
  affiché pour la période. (serveur ; UI-61)
- Stock visible par le caissier pendant la vente ? (UI-53)
- Sauvegardes, Mise à jour, Licence : menu « ⚙ » de l'administrateur ou toujours cachées ?
- Prix proposés par marge, changement de prix groupé ? (UI-10)
- Remise par ligne : suit la décision du TODO §4. (UI-52)
