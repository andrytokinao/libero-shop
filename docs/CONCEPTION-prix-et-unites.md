# Libero Shop — conception des prix et des unités

État au 16 octobre 2026, branche `develop`.

Document technique : comment l'application représente les **unités** d'un produit (unité de base,
conditionnements), ses **prix** (prix de vente, prix d'achat) et comment les deux se combinent
dans les ventes, les approvisionnements, les corrections de stock, l'import Excel et les rapports.
Il décrit le code tel qu'il est ; les écarts relevés à la lecture sont regroupés à la fin
(section 10).

Abréviations des chemins : `B/` = `backend/src/main/java/com/houssen/liberoshop/`,
`U/` = `ui/src/app/`.

---

## 1. Principes

1. **Tout le stock est compté dans l'unité de base du produit.** Un produit a une seule quantité
   en stock (`Product.stockQuantity`), exprimée dans son unité de base (le kapoka, le kg, la
   pièce…). Les autres unités ne sont que des façons de compter cette même quantité.
2. **Une unité = un facteur.** Chaque conditionnement dit combien d'unités de base il contient
   (`ProductPackaging.factor`). Vendre ou recevoir « 2 sacs » revient toujours à « 2 × facteur »
   unités de base.
3. **Chaque unité a son propre prix de vente**, saisi par le commerçant. Le prix d'un sac n'est
   *pas* calculé à partir du prix du kapoka : le gros peut être moins cher au détail près.
4. **Le prix d'achat est un coût moyen pondéré**, tenu par unité de base, mis à jour à chaque
   réception de marchandise.
5. **Ce qui est vendu est figé au moment de la vente.** Une ligne de vente recopie le nom de
   l'unité, son facteur, son prix et le coût d'achat du moment. Renommer une unité, la supprimer
   ou changer un prix ne réécrit jamais l'historique.
6. **Le client n'envoie jamais de prix.** La caisse envoie produit, unité et quantité ; le
   serveur relit les prix dans le catalogue, dans la transaction de la vente.
7. **Quantités décimales à 3 chiffres** partout (vente en vrac : 1,5 kg, 0,25 l), montants à
   2 chiffres.

---

## 2. Modèle de données

Le schéma est créé et mis à jour par Hibernate (`spring.jpa.hibernate.ddl-auto=update`, base H2
fichier). Il n'y a pas de Flyway : seules quelques migrations écrites à la main existent
(section 9).

### 2.1 Vue d'ensemble

```
Category 1───* Product 1───* ProductPackaging ──┐
                  │  (unit, price, averageCost,   │ reference (0..1)
                  │   stockQuantity)              └──► ProductPackaging
                  │
                  ├───* SaleLine *───1 Sale 1───1 Invoice
                  │     (copie : unitLabel, unitFactor, unitPrice, unitCost)
                  │
                  └───* StockMovement  (table unique, colonne movement_type)
                         ├─ Supply        "SUPPLY"      (+ unitLabel, unitFactor, unitCost, supplier)
                         ├─ StockOutput   "OUTPUT"      (+ invoice)
                         └─ StockAdjustment "ADJUSTMENT" (+ reason, comment ; quantité signée)
```

### 2.2 `Product` — `B/entity/Product.java`

| Champ | Colonne | Rôle |
|---|---|---|
| `unit` | `varchar(16)`, nullable | Nom de l'**unité de base**, tel que saisi. Null = « unités » sans nom. |
| `price` | `numeric(12,2)`, non null | Prix de vente d'**une unité de base**. |
| `averageCost` | `numeric(18,6)`, nullable | **Prix d'achat** : coût moyen pondéré d'une unité de base, à 6 décimales (une tonne saisie se relit à l'ariary). Null tant qu'aucun coût n'est connu. |
| `purchaseUnit` | `purchase_unit_id`, nullable | **Unité d'achat** : le conditionnement dans lequel le prix d'achat se saisit et s'affiche (la tonne). Null = unité de base. Remis à null quand l'unité est supprimée. |
| `stockQuantity` | `numeric(14,3)`, non null | Stock, **en unités de base**. |
| `barcode` | unique, nullable | Code-barres de l'unité de base. |
| `category` | `category_id`, nullable | Rayon / famille (arbre de 4 niveaux max). |
| `packagings` | `@OneToMany(mappedBy = "product")`, triés par facteur croissant | Les autres unités. Lecture seule depuis le produit. |

Le stock ne change que par `adjustStock(quantité)`, qui ramène le résultat à 3 décimales.

L'unité de base **n'est pas une ligne** de `product_packaging` : son nom, son prix, son
code-barres et le stock sont sur le produit lui-même. Cela garde un seul endroit pour le stock et
évite un conditionnement « facteur 1 » à maintenir en double.

Pas de `@Version` : les écritures concurrentes sont sérialisées par un verrou pessimiste,
`ProductRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`). Toute opération qui modifie un
produit le prend, même quand elle ne touche pas au stock : Hibernate réécrit toutes les colonnes
de la ligne, et une vente validée entre la lecture et l'écriture verrait son décompte écrasé
(correctif `9c0a5c3`, renommage de l'unité de base).

### 2.3 `ProductPackaging` — `B/entity/ProductPackaging.java`

Un conditionnement d'un produit : « sac 50 kg », « carton de 12 », « kg » pour un produit compté
en kapoka.

| Champ | Colonne | Rôle |
|---|---|---|
| `label` | `varchar(16)`, non null | Nom de l'unité. Unique par produit (contrainte `(product_id, label)`). |
| `reference` | `reference_id`, nullable | L'unité **dans laquelle** celle-ci est décrite. Null = l'unité de base. |
| `referenceQuantity` | `numeric(12,3)`, nullable | Combien d'unités de référence elle contient (« 50 » kg pour un sac de 50 kg). |
| `factor` | `numeric(12,3)`, non null | Combien d'**unités de base** elle contient. **Calculé**, jamais saisi : `referenceQuantity × reference.factor`. |
| `price` | `numeric(12,2)`, non null | **Son propre** prix de vente. |
| `barcode` | unique, nullable | Son code-barres (un sac et un paquet ont des codes différents). |

**Pourquoi une référence ?** Le commerçant pense « un sac = 50 kg », pas « un sac = 175 kapoka ».
On lui laisse écrire chaque unité dans celle qui lui parle, et le serveur en déduit le facteur.

Exemple — riz vendu au kapoka :

| Unité | Écrite comme | `referenceQuantity` | `reference` | `factor` (en kapoka) |
|---|---|---|---|---|
| kapoka (base) | — | — | — | 1 |
| kg | 3,5 kapoka | 3,5 | null (base) | 3,5 |
| sac 50 kg | 50 kg | 50 | kg | 175 |

Quand le facteur d'une unité change (ici le kg passe à 3,6 kapoka), toutes les unités écrites
dedans, directement ou en chaîne, sont recalculées (le sac passe à 180), et **toutes** sont
revalidées avant la moindre écriture (`PackagingRules.followersOf`).

### 2.4 `SaleLine` — `B/entity/SaleLine.java`

Ce qui a été vendu, figé :

| Champ | Colonne | Rôle |
|---|---|---|
| `quantity` | `numeric(12,3)` | Quantité **dans l'unité vendue** (2 sacs, pas 350 kapoka). |
| `unitLabel` | `varchar(16)` | Copie du nom de l'unité au moment de la vente. |
| `unitFactor` | `numeric(12,3)` | Copie du facteur. Null sur les lignes anciennes, lu comme 1. |
| `unitPrice` | `numeric(12,2)` | Copie du prix de l'unité vendue. |
| `unitCost` | `numeric(12,2)`, nullable | Coût d'achat de l'unité vendue au moment de la vente : `averageCost × facteur`. Null si aucun coût n'était connu. |

Pas de clé étrangère vers `product_packaging` : un conditionnement supprimé ou renommé ne touche
pas aux ventes passées.

- `baseQuantity()` = `quantity × facteur`, à 3 décimales — ce qui sort du stock.
- `lineTotal()` = `unitPrice × quantity`, arrondi au centime (HALF_UP).
- `Sale.totalAmount` = somme des `lineTotal()`.

### 2.5 Mouvements de stock — `B/entity/StockMovement.java` et sous-classes

Une table unique `stock_movement` ; `quantity` (`numeric(14,3)`) est **toujours en unités de
base**.

- **`Supply`** (approvisionnement) : `unitCost` = prix d'achat d'**une unité telle que reçue**
  (un sac), `unitLabel`/`unitFactor` = l'unité de réception (null si reçue en unité de base),
  `supplier` facultatif. Valeurs dérivées :
  - `receivedQuantity()` = `quantity / facteur` (les 2 sacs d'origine) ;
  - `baseUnitCost()` = `unitCost / facteur` (le prix d'achat d'un kapoka) ;
  - `totalCost()` = `unitCost × receivedQuantity`.
- **`StockOutput`** : une sortie par ligne de facture, en unités de base, avec `saleValue` =
  le total de la ligne de vente (ce que ces marchandises ont été vendues). Null sur les sorties
  antérieures, valorisées alors au prix actuel de l'unité de base (`StockOutput.value()`).
- **`StockAdjustment`** (correction) : quantité **signée** (l'écart, pas le chiffre compté),
  motif (`BREAKAGE`, `THEFT`, `EXPIRED`, `INVENTORY_COUNT`, `OTHER`), commentaire.

### 2.6 Règles de quantité — `B/util/Quantities.java`

- Échelle 3, précision 14 (`SCALE`, `PRECISION`).
- `scaled()` arrondit à 3 décimales (HALF_UP) ; `tooPrecise()` signale plus de 3 décimales.
- Les écrans et les services **refusent** une quantité trop précise (`QUANTITY_TOO_PRECISE`) ;
  seul l'import arrondit, et le note sur la ligne.

---

## 3. Unités : règles de gestion

Services : `B/service/ProductUnitService.java` (opérations) et `B/service/PackagingRules.java`
(validation, sans transaction propre pour être appelé aussi par l'import).

### 3.1 Unité de base

- Renommer (`PUT /api/products/{id}/units/base`) **ne fait que changer le nom** : le stock, les
  facteurs et les prix restent tels quels. Un nom vide remet « unités ».
- Le nom ne doit pas être déjà celui d'un conditionnement du produit.
- Opération faite sous verrou du produit (voir 2.2).

### 3.2 Conditionnements

Ajout, modification (`POST`/`PUT /api/products/{id}/units[/{packagingId}]`) — tout est validé
avant d'écrire :

| Règle | Code d'erreur |
|---|---|
| Nom obligatoire, espaces ramenés à un seul | `PACKAGING_LABEL_REQUIRED` |
| Nom unique dans le produit, **unité de base comprise**, comparé sans casse ni espaces (« Sac 50kg » = « sac 50 kg ») | `PACKAGING_LABEL_TAKEN` |
| Référence : vide ou nom de l'unité de base = unité de base ; sinon un conditionnement existant | `PACKAGING_REFERENCE_UNKNOWN` |
| Pas de boucle (A écrit en B écrit en A) | `PACKAGING_REFERENCE_CYCLE` |
| Contenance > 0, au plus 3 décimales | `PACKAGING_FACTOR_INVALID`, `PACKAGING_FACTOR_TOO_PRECISE` |
| Facteur calculé > 0, au plus 3 décimales — **refusé, pas arrondi** | idem |
| Facteur ≠ 1 : une unité qui vaut exactement une unité de base *est* l'unité de base | `PACKAGING_FACTOR_IS_BASE` |
| Prix > 0 | `PACKAGING_PRICE_INVALID` |
| Code-barres unique sur les produits **et** les conditionnements | `BARCODE_TAKEN` |

Un facteur inférieur à 1 est permis (une « demi-baguette » d'un produit compté à la baguette). Si
une unité demande plus de 3 décimales, le message conseille de prendre la plus petite des deux
comme unité de base.

Suppression (`DELETE …/units/{packagingId}`) : refusée tant qu'une autre unité est écrite dedans
(`PACKAGING_IS_REFERENCE`). L'historique n'est pas concerné (copies dans `SaleLine` et `Supply`).

Toute réponse renvoie l'état complet des unités du produit (`ProductUnitsResponse`) : l'écran se
redessine à partir de cette réponse, sans recalcul de son côté.

### 3.3 Résolution d'une unité — `B/service/SaleUnit.java`

Point unique utilisé par la vente **et** l'approvisionnement :

```java
SaleUnit.of(product, packagingId)
  packagingId == null → (product.unit, facteur 1, product.price)
  sinon               → (packaging.label, packaging.factor, packaging.price)
                        ou UNKNOWN_SALE_UNIT si le conditionnement n'existe plus
```

---

## 4. Prix de vente

- **Unité de base** : `Product.price`, obligatoire et > 0 à la création et à la modification
  (`PRODUCT_PRICE_INVALID`).
- **Conditionnement** : `ProductPackaging.price`, obligatoire et > 0, saisi librement.
  L'écran propose `arrondi(facteur × prix de base)` en simple indication (placeholder du champ
  dans `product-units-dialog`), jamais imposé.
- **Modification** : `PUT /api/products/{id}` (nom, prix, code-barres, catégorie, prix d'achat).
  Elle publie un `StockChangedEvent` : les caisses ouvertes reçoivent le nouveau prix en direct.
- Les ventes déjà faites gardent leur prix (`SaleLine.unitPrice`).

---

## 5. Prix d'achat (coût moyen pondéré)

Classe : `B/service/PurchaseCosting.java`. Le prix d'achat est `Product.averageCost`, **par unité
de base**, à 6 décimales (`AVERAGE_SCALE`, depuis le 16 octobre 2026 ; 2 avant). Il se saisit dans
l'**unité d'achat** du produit (`purchaseUnit`) et se divise par son facteur
(`averageFrom`) — voir `28-configuration-des-prix-et-des-unites.md`.

### 5.1 Calcul à chaque réception

```
averageAfterReceipt(stockAvant, moyenneAvant, reçu, coût) :
  coût inconnu                            → moyenne inchangée
  pas encore de moyenne, ou stockAvant ≤ 0 → coût
  reçu ≤ 0                                → moyenne inchangée
  sinon → (stockAvant × moyenneAvant + reçu × coût) / (stockAvant + reçu), arrondi au centime
```

Un stock nul ou négatif repart du dernier coût : il n'y a plus rien à « moyenner ».

Exemple : 100 kapoka en stock à 800 Ar ; réception de 2 sacs de 50 kg (175 kapoka chacun) à
145 000 Ar le sac.
- coût par kapoka reçu = 145 000 / 175 = 828,57 ;
- nouvelle moyenne = (100 × 800 + 350 × 828,57) / 450 = 822,22 Ar le kapoka.

### 5.2 Qui modifie le prix d'achat

| Opération | Effet sur `averageCost` |
|---|---|
| Approvisionnement (`StockService.registerSupply`) | Moyenne pondérée avec `baseUnitCost()` de la réception. |
| Création d'un produit avec stock initial et prix d'achat (`ProductEditService.create`) | Le prix saisi devient la moyenne ; un `Supply` sans fournisseur est écrit si le stock > 0. |
| Import Excel, produit nouveau ou fusionné | Moyenne pondérée, comme une réception. |
| Modification à la main (`PUT /api/products/{id}`, champs `purchaseCost` et `purchaseUnitId`) | **Remplace** la moyenne : prix d'une unité d'achat ÷ son facteur. Champ vide → moyenne inchangée (on ne peut pas l'effacer). |
| Vente, annulation, correction de stock | **Aucun**. Une casse ou une perte ne change pas le coût des unités restantes. |

### 5.3 Coût figé à la vente

`PurchaseCosting.costOfSale(product, facteur)` = `averageCost × facteur`, copié dans
`SaleLine.unitCost`. Le rapport de marge utilise **ce coût figé**, pas la moyenne du jour : une
hausse du prix d'achat après la vente ne change pas la marge déjà réalisée.

---

## 6. Flux

### 6.1 Vente — `B/service/SaleService.checkout`

Entrée : `CreateSaleRequest.lines[{productId, packagingId?, quantity}]` (+ `clientReference`).

1. **Regroupement** (`mergeLines`) : refus des quantités à plus de 3 décimales, fusion des lignes
   même produit + même unité. Le même produit dans deux unités reste sur deux lignes.
2. **Verrous** : `findByIdForUpdate` sur chaque produit, **dans l'ordre des identifiants** pour
   éviter les interblocages entre deux caisses.
3. Panier déjà enregistré sous la même référence → la facture existante est rendue.
4. **Gel de chaque ligne** : `SaleUnit.of` → `unitLabel`, `unitFactor`, `unitPrice` ;
   `unitCost = costOfSale`.
5. **Contrôle du stock** (`refuseShortages`) : somme des `baseQuantity()` **par produit, toutes
   unités confondues**, comparée au stock. Une seule erreur `InsufficientStockException` liste
   tous les produits manquants, avec le stock disponible ; la caisse met son affichage à jour.
6. **Décompte** : `adjustStock(-baseQuantity)` ; une `StockOutput` par ligne en unités de base.
7. Total, facture, événements (`SaleRecordedEvent`, `StockChangedEvent`).

Arrondis : quantité de base à 3 décimales, total de ligne au centime, total = somme des lignes,
coût figé au centime.

Les commandes par QR code (`OnlineOrderService`) passent par le même `checkout`, avec le même
choix d'unité (`PublicMenuResponse.Unit(id, label, price)`).

Annulation (`InvoiceService.giveBackStock`) : stock rendu en unités de base sous verrou, puis
sorties **supprimées** (et non contre-passées — point ouvert du `TODO.md`).

### 6.2 Approvisionnement — `B/service/StockService.registerSupply`

Entrée : `CreateSupplyRequest{productId, packagingId?, quantity, unitCost?, supplierId?}` —
rôle `DEPOT_MANAGER`.

1. Verrou du produit, contrôle des décimales.
2. `SaleUnit.of(product, packagingId)` : on reçoit dans n'importe quelle unité du produit.
3. `Supply.quantity = quantité × facteur` (unités de base) ; `unitCost` = prix **par unité
   reçue** ; `unitLabel`/`unitFactor` = unité de réception (null pour l'unité de base).
4. `PurchaseCosting.receive(product, quantitéBase, supply.baseUnitCost())` : nouvelle moyenne,
   puis ajout au stock.

L'écran pré-remplit le prix d'achat avec `dernier coût par unité de base × facteur` de l'unité
choisie, et avertit quand le prix d'achat dépasse le prix de vente de cette unité.

### 6.3 Correction de stock — `StockService` (corrections)

- **En unité de base uniquement** (pas de `packagingId` dans la requête).
- Inventaire (`INVENTORY_COUNT`) : on saisit ce qui a été compté ; l'écart est calculé sur la
  ligne verrouillée. Écart nul refusé.
- Autres motifs : quantité > 0 et ≤ stock ; `OTHER` exige un commentaire.
- Le coût moyen ne bouge pas.

### 6.4 Import Excel — `ProductImportService`, `ProductImportColumns`, `ProductUnitSheet`

- **Feuille « Produits »** : colonnes reconnues par leur en-tête, dans n'importe quel ordre —
  nom, quantité, **unité**, **prix** (« prix de vente », « pv », « pu »…), **prix d'achat**
  (« prix d'achat », « pa », « coût », « prix de revient »…), code-barres, catégorie. Les nombres
  acceptent espaces ou points de milliers, virgule décimale et suffixe monétaire.
- **Feuille « Unités »** : produit, unité, contenance, unité de référence, prix, code-barres. Une
  ligne de contenance 1 sans référence décrit l'unité de base. Au plus 10 unités par produit ;
  les références sont créées avant les unités écrites dedans.
- **Conversion** (`toBaseUnit`) : une ligne saisie dans une autre unité est ramenée à l'unité de
  base (`quantité × facteur`, `coût / facteur`). Une unité inconnue du produit est **refusée**,
  jamais devinée. Les unités déjà au catalogue l'emportent sur celles du fichier.
- **Fusion** avec un produit existant : stock ajouté et coût moyenné ; **prix, nom et catégorie
  existants conservés** ; seuls les conditionnements manquants sont ajoutés.
- Chaque ligne importée écrit un `Supply` en unités de base.
- Une unité refusée est notée sur sa ligne sans annuler le reste de l'import.

---

## 7. Rapports

Service : `B/service/CostingService.java`.

| Rapport | Calcul | Source des prix |
|---|---|---|
| **Marge sur période** (`GET /api/costing/margin`, super-admin) — page « Bénéfices & stock » | Par produit : quantité en unités de base, chiffre d'affaires `Σ unitPrice × quantity`, coût `Σ unitCost × quantity`. Ventes payées et non payées, sans les annulées. Les lignes sans coût connu sont comptées à part (`uncostedRevenue`, taux de couverture). Taux de marque = marge / CA. | **Valeurs figées** des lignes de vente. |
| **Coût par produit** (`/api/costing/products`) | Marge unitaire = `price − averageCost`, dernier coût de réception, comparaison des fournisseurs par unité de base. | Prix et moyenne **actuels**, unité de base. |
| **Valeur du stock** (`/api/costing/stock-valuation`) | Par catégorie : `stock × price` (valeur de vente, au détail) et `stock × averageCost` (valeur d'achat). | Prix et moyenne **actuels**, unité de base. |
| **Sorties de stock** (`/api/stock/outputs`) | Valeur de chaque sortie et total de la recherche. | **Montant figé** de la ligne de vente (`saleValue`). |
| **Stock bas** (`StockPolicy`) | Stock < 10 unités de base, seuil unique pour tout le catalogue. | — |

---

## 8. Interface (Angular)

| Élément | Fichier | Rôle |
|---|---|---|
| Unités d'un produit | `U/core/sale/sale-unit.ts` | `unitsOf` : unité de base puis conditionnements par facteur croissant ; `stockLabel` affiche le stock dans la plus grande unité qu'il couvre (« 9,96 sac 50 kg ») ; `parseQuantity`/`formatQuantity` à 3 décimales, format français. |
| Panier | `U/core/sale/cart.ts` | Ligne = produit + unité ; contrôle du stock en unités de base sur toutes les lignes du produit ; changer l'unité d'une ligne garde le nombre et fusionne si besoin ; envoie `{productId, packagingId, quantity}`, jamais de prix. |
| Choix de l'unité | `U/shared/components/unit-picker.component.ts`, `cart-line-quantity.component.ts` | Un bouton par unité avec son prix ; grisé si le stock ne couvre pas une unité entière. |
| Fiche produit | `U/shared/components/product-units-dialog.component.ts` (« Modifier le produit — … ») | Nom, prix par unité de base, code-barres, catégorie, prix d'achat par unité de base ; renommage de l'unité de base ; tableau des unités (« contient 50 kg (175 kapoka) ») ; ajout/modification avec choix de la référence (boucles exclues), prix suggéré en indication, phrase d'aperçu. |
| Création | `U/shared/components/product-create-dialog.component.ts` | Prix, unité de base, catégorie, code-barres, prix d'achat, stock initial ; alerte si coût ≥ prix. |
| Réception | `stock-entry-dialog.component.ts`, `U/features/depot-manager/supply.component.ts` | « Reçu en » (toutes les unités), prix d'achat par unité reçue pré-rempli, aperçu en unités de base, marge unitaire, création d'un conditionnement à la volée. |
| Correction | `stock-adjust-dialog.component.ts` | En unité de base. |
| Import | `U/features/depot-manager/product-import-dialog.component.ts` | Aperçu ligne à ligne ; prix affiché en lecture seule pour une fusion. |

Menu « ⋮ » d'un produit (`product-menu.component.ts`, droits dans `U/core/catalog/product-rights.ts`) :
ajouter du stock (gestionnaire de dépôt), modifier, corriger, historique (gestionnaire de dépôt et
super-admin).

---

## 9. Points d'entrée et schéma

### 9.1 API

| Méthode | Chemin | Rôles |
|---|---|---|
| POST | `/api/products` | DEPOT_MANAGER, SUPER_ADMIN |
| PUT | `/api/products/{id}` (prix, prix d'achat, nom, code-barres, catégorie) | DEPOT_MANAGER, SUPER_ADMIN |
| GET | `/api/products/{id}/units` | tout compte connecté |
| PUT | `/api/products/{id}/units/base` | DEPOT_MANAGER, SUPER_ADMIN |
| POST / PUT / DELETE | `/api/products/{id}/units[/{packagingId}]` | DEPOT_MANAGER, SUPER_ADMIN |
| POST | `/api/sales` | CASHIER, ORDER_TAKER |
| POST | `/api/public/tables/{token}/orders` | public (réseau local) |
| POST | `/api/stock/supplies` | DEPOT_MANAGER |
| GET / POST | `/api/stock/adjustments` | DEPOT_MANAGER, SUPER_ADMIN |
| GET | `/api/costing/products`, `/products/{id}`, `/products/{id}/suppliers`, `/stock-valuation` | DEPOT_MANAGER, SUPER_ADMIN |
| GET | `/api/costing/margin` | SUPER_ADMIN |
| POST / GET | `/api/products/import/preview`, `/apply`, `/template`, `/columns` | DEPOT_MANAGER, SUPER_ADMIN |

### 9.2 Migrations

- `B/bootstrap/DecimalQuantityMigration.java` : passage des quantités entières en
  `numeric(p,3)` (`product.stock_quantity`, `stock_movement.quantity`, `sale_line.quantity`).
- `B/bootstrap/MovementTypeMigration.java` : contrainte `movement_type` élargie à `ADJUSTMENT`.
- `B/bootstrap/AverageCostPrecisionMigration.java` : `product.average_cost` passé à
  `numeric(18,6)`.
- Les colonnes `average_cost`, `unit_cost`, `unit_label`, `unit_factor`, `reference_id`,
  `reference_quantity` et la table `product_packaging` sont créées par `ddl-auto=update`
  (colonnes nullables, rien à reprendre).

### 9.3 Tests de référence

`backend/src/test/java/com/houssen/liberoshop/service/` : `SaleByUnitTest`, `SupplyByUnitTest`,
`ProductUnitServiceTest` (références en chaîne, boucles, suppression), `PurchaseCostingTest`,
`CostingServiceTest`, `ProductEditServiceTest`, `ProductByHandTest`, `StockAdjustmentTest`,
`ProductImportServiceTest`.

---

## 10. Limites et écarts relevés

Constatés à la lecture du code. Les deux premiers ont été traités le 16 octobre 2026
(`27-valeur-des-sorties-au-prix-de-vente.md`) ; les autres restent ouverts.

1. **Valeur du stock au prix de détail — convention assumée.** La valeur de vente du stock et
   le coût par produit utilisent le prix de l'unité de base : la part du stock qui partira en
   sac ou au détail n'est pas connue d'avance. L'écran « Bénéfices & stock » l'indique désormais
   (« vendu au détail, au prix actuel de l'unité de base »). La valeur d'achat, elle, ne dépend
   d'aucune convention. (Le rapport de marge utilise les prix réellement vendus.)
2. **Valeur des sorties de stock — corrigé.** Chaque sortie garde le montant de sa ligne de
   vente (`StockOutput.saleValue`, colonne `sale_value`) : prix et unité de la vente, insensible
   aux changements de prix. Les sorties enregistrées avant gardent l'ancien calcul (prix actuel
   de l'unité de base × quantité).
3. **Valeur d'une correction au coût du jour.** `StockAdjustmentResponse.costValue` utilise la
   moyenne actuelle, pas celle du moment de la correction.
4. **Réceptions en unité de base renommées après coup.** Un `Supply` reçu en unité de base garde
   `unitLabel = null` ; l'historique affiche le nom **actuel** de l'unité de base.
5. **L'import perd l'unité de réception.** Ses `Supply` sont écrits en unités de base sans
   `unitLabel`/`unitFactor` : « 2 tonnes à X » devient « 7 000 kapoka à X / 3 500 ».
6. **Prix à 0 accepté par l'import** (`ProductImportLineRequest.price` en `@PositiveOrZero`),
   refusé partout ailleurs (déjà au `TODO.md`, section 3).
7. **Conditionnements sans verrou ni version.** Ajout, modification et suppression d'une unité ne
   verrouillent pas le produit : deux modifications simultanées de la même unité — la dernière
   gagne. Sans incidence sur le stock (non écrit par ces opérations).
8. **Infobulle de l'import (à vérifier).** `unitsTitle` dans `product-import-dialog.component.ts`
   affiche `unit.factor` suivi du nom de l'unité de base ; pour une unité écrite dans une autre
   (« sac 25 kg = 25 kg »), cela pourrait afficher « 25 kapoka ».
9. **Seuil de stock bas aveugle aux unités** : 10 unités de base pour tout produit (10 kapoka
   comme 10 sacs) — déjà au `TODO.md` (seuil par produit).
10. **Corrections de stock en unité de base seulement** — déjà au `TODO.md`.
11. **Aucune trace de qui a changé un prix ou une unité** — piste d'audit au `TODO.md`.

## Voir aussi

- `22-produits-et-stock-a-la-main.md` : fonctionnement côté utilisateur (ajout de stock, prix
  d'achat modifiable, corrections, historique, droits).
- `21-produits-en-liste-a-la-vente.md` : choix de l'unité dans la liste de vente.
- `REALISATION-partie-3-argent.md` : référence de panier, ventes en double.
