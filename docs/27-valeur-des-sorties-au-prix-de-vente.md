# Libero Shop — valeur des sorties de stock au prix de vente

Réalisée le 16 octobre 2026.

Demande directe, suite à `CONCEPTION-prix-et-unites.md` (section 10, écarts 1 et 2).

---

## Ce qui change

**Sorties de stock (écran « Sorties de stock » du dépôt).**

- **Avant.** La valeur de chaque sortie, et le total en haut de l'écran, étaient recalculés à
  chaque affichage : quantité en unités de base × prix **actuel** de l'unité de base. Deux
  conséquences :
  - un article vendu au kg ou au sac, à un prix différent de « facteur × prix au détail »,
    n'avait pas la même valeur que sur la facture (2 kg vendus 6 000 Ar apparaissaient à
    6 300 Ar, soit 7 kapoka à 900 Ar) ;
  - un changement de prix modifiait la valeur de toutes les sorties passées.
- **Après.** Chaque sortie garde le montant de sa ligne de vente au moment de la vente. La valeur
  affichée et le total correspondent aux factures et ne bougent plus quand un prix change.
- **Sorties enregistrées avant cette version :** elles n'ont pas ce montant et gardent l'ancien
  calcul (prix actuel). Seules les nouvelles ventes en profitent.

**Valeur du stock (page « Bénéfices & stock »).**

- La valeur de vente du stock reste calculée **au prix de l'unité de base** (le détail). Ce n'est
  pas une erreur de calcul mais une convention : on ne sait pas d'avance quelle part du stock
  partira en sac à prix de gros et quelle part au détail. La valeur d'achat ne dépend d'aucune
  convention.
- L'indication sous le chiffre le dit désormais : « vendu au détail, au prix actuel de l'unité de
  base » (au lieu de « ce qu'il rapporterait vendu au prix actuel »).

## Détail

- `StockOutput` : nouvelle colonne `sale_value` (`numeric(12,2)`, nullable, créée par
  `ddl-auto=update`), et `value()` qui la rend, ou l'ancien calcul quand elle est vide.
- `SaleService.checkout` et `DataInitializer` (données de démo) : `saleValue = line.lineTotal()`.
- `StockOutputResponse.value` : `output.value()`.
- `StockMovementRepository.sumOutputs` : `sum(coalesce(o.saleValue, o.quantity * p.price))`.
- `CostingService.stockValuation` : javadoc de la convention ; `margin.component.ts` : texte
  de l'indication.
- `CONCEPTION-prix-et-unites.md` (et sa version HTML) : sections 2.5, 7 et 10 mises à jour.

## Vérifications faites

- Nouveau test `SaleByUnitTest.outputValuedAtTheSale` : 2 kg vendus 6 000 Ar, prix au détail
  changé ensuite ; la sortie, la ligne de l'écran et le total valent 6 000 Ar.
- Tests serveur : 587, tous verts.

## Non vérifié

- Pas vu à l'écran (paquet de l'interface non reconstruit sur ce poste, voir
  `24-navigateur-apres-installation-et-sauvegardes-cachees.md`).

## Reste ouvert

- Reprendre la valeur des sorties anciennes à partir des lignes de vente : possible quand une
  facture n'a qu'une ligne pour le produit, ambigu sinon. Non fait.
- Le total des quantités de l'écran additionne des unités de base de produits différents
  (kapoka + pièces) : inchangé.
