# Libero Shop — configuration des prix et des unités

Réalisée le 16 octobre 2026.

Demande directe : une partie « configuration » pour chaque produit (fenêtre) et pour tous les
produits — prix d'entrée, prix de vente, unités, bénéfice par unité — avec ajout de stock,
affichage selon le rôle et code-barres scanné ou tapé.

---

## Ce qui change

**Le prix d'entrée se saisit dans l'unité d'achat.**

- Chaque produit a désormais une **unité d'achat** : l'unité de base (par défaut) ou une de ses
  unités — la tonne, le sac. Le prix d'entrée se tape dans cette unité : « 2 750 000 Ar par
  tonne ». Rien à déclarer pour le kapoka : l'application en déduit 785,71 Ar le kapoka et
  calcule le prix d'entrée de toutes les autres unités.
- La phrase sous le champ relit ce qui a été compris : « 1 tonne = 3 500 kapoka : soit 785,71 Ar
  le kapoka. »
- Le prix relu est **exactement** celui tapé (2 750 000 Ar, pas 2 749 985 Ar) : le prix moyen
  par unité de base est maintenant gardé à 6 décimales au lieu de 2.
- La tonne doit d'abord exister comme unité du produit (« tonne contient 1 000 kg » ou
  « 3 500 kapoka »), ajoutée dans la même fenêtre.

**Fenêtre « Configuration » d'un produit** (menu « ⋮ » → « Configurer (prix, unités) »,
anciennement « Modifier le produit »).

- En haut : nom, catégorie, code-barres ; puis **prix de vente** (par unité de base) et **prix
  d'entrée** avec le choix de l'unité d'achat.
- Le tableau des unités montre pour chacune : contenance, **prix de vente**, **prix d'entrée**,
  **bénéfice** sur une unité vendue et la **marge** en % (en rouge si l'unité est vendue à
  perte). L'unité d'achat porte la mention « achat ».
- Bouton **« ＋ Ajouter du stock »** en bas, pour le responsable de dépôt : la réception s'ouvre
  sur l'unité d'achat, au dernier prix payé.

**Page « Prix et unités »** (nouvelle entrée de menu, responsable de dépôt et administrateur).

- Tous les produits, une ligne par unité : prix de vente, prix d'entrée, bénéfice par unité,
  marge. Recherche par nom ; filtres « Sans prix d'entrée » et « Vendus à perte ».
- Trois compteurs : produits, produits sans prix d'entrée, produits vendus à perte.
- Par produit : « Configurer » (ouvre la fenêtre ci-dessus) et, pour le responsable de dépôt,
  « ＋ Stock ».

**Selon le rôle.**

| Rôle | Ce qu'il voit |
|---|---|
| Responsable de dépôt (approvisionnement) | page et fenêtre complètes, avec « Ajouter du stock » |
| Administrateur | page et fenêtre complètes, sans ajout de stock (le serveur réserve les réceptions au dépôt) |
| Caisse, prise de commande, agent de dépôt | rien de nouveau : les prix d'entrée ne leur sont jamais envoyés |

**Code-barres : scanné ou tapé.**

- Dans la fenêtre de configuration (unité de base et autres unités) et dans « Nouveau produit » :
  saisie au clavier, **douchette USB** (qui tape le code puis Entrée — Entrée ne valide plus le
  formulaire à moitié rempli), ou **bouton 📷** dans l'application mobile (caméra du téléphone).

**Réceptions.** L'écran « Approvisionnement » et la fenêtre « Ajouter du stock » pré-remplissent
le prix d'achat calculé par le serveur à partir du prix exact : une tonne revient à l'ariary
près. Une réception en tonnes nourrit aussi le prix moyen sans arrondi au centime.

## Recommandations suivies

- **Le prix d'entrée reste un coût moyen par unité de base**, comme avant : seules la saisie et
  l'affichage se font dans l'unité d'achat. Ainsi une réception en sacs et une autre en tonnes
  se mélangent correctement, et la marge des ventes reste figée au moment de la vente.
- **Pas de nouveau prix par unité à maintenir** : le prix d'entrée de chaque unité se calcule
  (prix moyen × contenance). Seuls les prix de vente sont saisis unité par unité, comme avant.
- **Une seule fenêtre de configuration** (l'ancienne « Modifier le produit », complétée) plutôt
  qu'une deuxième fenêtre qui ferait la même chose.

## Détail

Serveur :

- `Product.averageCost` : `numeric(18,6)` ; `Product.purchaseUnit` (colonne
  `purchase_unit_id`, nullable, vers `product_packaging`).
- `AverageCostPrecisionMigration` : élargit `product.average_cost` à 6 décimales sur une base
  existante, une seule fois, valeurs gardées.
- `PurchaseCosting.AVERAGE_SCALE = 6`, `averageFrom(coût, facteur)`, `average(...)` ; les
  montants (coût figé d'une ligne de vente, valeurs) restent à 2 décimales.
- `Supply.exactBaseUnitCost()` : coût par unité de base à 6 décimales, utilisé par
  `StockService.registerSupply`.
- `UpdateProductRequest.purchaseUnitId` ; `purchaseCost` est le prix d'**une unité d'achat**.
  Unité d'un autre produit ou supprimée : `PURCHASE_UNIT_UNKNOWN`.
- `ProductUnitService.remove` : verrouille le produit ; supprimer l'unité d'achat ramène l'achat
  à l'unité de base (le prix d'entrée est gardé).
- `ProductUnitsResponse.purchaseUnitId` ; `ProductCostResponse.purchaseUnitId` et `units` (par
  unité : prix de vente, prix d'entrée, bénéfice, marge, dernier prix payé).
- `CostingService.stockValuation` : valeurs arrondies au centime.
- Import : le coût de deux lignes fusionnées reste arrondi au centime (inchangé).

Écran :

- `product-units-dialog` → « Configuration — … » ; `product-dialogs` enchaîne sur l'ajout de
  stock ; `stock-entry-dialog` et `supply.component` pré-remplissent avec `units[].lastCost`.
- Nouvelle page `features/catalog/product-prices.component.ts`, routes
  `/gestion-depot/prix-et-unites` et `/admin/prix-et-unites`, icône `tag`.
- `product-create-dialog` : code-barres avec bouton caméra.

## Vérifications faites

- Tests serveur : 573 dans les rapports, tous verts. Nouveaux : prix saisi à la tonne et relu à
  l'ariary, unité d'un autre produit refusée, bénéfice par unité (`CostingServiceTest`),
  suppression de l'unité d'achat, migration de la colonne. Les tests existants attendent
  désormais des prix moyens à 6 décimales (828,571429 au lieu de 828,57).
- Tests écran : 189, tous verts, dont 4 nouveaux pour la page « Prix et unités » (lignes par
  unité, compteurs, filtre « à perte », bouton de stock selon le rôle).
- Compilation Angular avec contrôle strict des gabarits (`ngc`) : sans erreur.

## Non vérifié

- **Pas vu à l'écran** : le paquet de l'interface ne se construit pas sur ce poste (esbuild
  installé pour une autre plateforme, voir `24-…`). Les écrans sont compilés et testés, pas
  essayés dans un navigateur.
- Migration de la colonne essayée sur H2 seulement.
- Bouton caméra : composant existant réutilisé, non essayé sur téléphone.

## Pour essayer

1. Responsable de dépôt → **Prix et unités** : la liste des produits et de leurs unités.
2. « Configurer » sur un riz compté en kapoka → ajouter l'unité « tonne » contenant 3 500
   kapoka (ou 1 000 kg).
3. En haut, prix d'entrée : choisir « par tonne », taper 2 750 000, Enregistrer → la tonne affiche
   2 750 000 Ar de prix d'entrée, le kapoka 785,71 Ar, chacun avec son bénéfice.
4. « ＋ Ajouter du stock » → la réception s'ouvre en tonnes.

## Reste ouvert

- Création d'un produit : le prix d'achat se saisit encore par unité de base (les autres unités
  n'existent pas avant la création).
- Import Excel : le prix d'achat d'une ligne dans une autre unité est toujours ramené au centime
  par unité de base (écart 5 de `CONCEPTION-prix-et-unites.md`).
- La page « Prix et unités » charge tous les produits en une fois, comme « Approvisionnement ».
