# Libero Shop — sorties de stock lues par pages

Première tranche de la partie 6 (pagination). Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 5, « Aucune pagination » — pour les sorties de
stock seulement. Les factures restent à faire (voir « Reste ouvert »).

---

## Ce qui change

**Avant.** L'écran « Sorties » du responsable dépôt demandait au serveur **toutes** les sorties
depuis le premier jour, à chaque ouverture et à chaque lettre tapée dans la recherche. Or une
sortie est écrite pour chaque ligne de chaque vente : c'est la liste qui grossit le plus vite de
toute l'application. Une boutique qui vend 200 lignes par jour en a 70 000 au bout d'un an.

**Après.**

- L'écran reçoit **50 sorties à la fois**, les plus récentes d'abord, avec un bouton
  « Afficher plus » et l'indication « 50 sur 1 240 ».
- **La recherche est faite par la base**, plus par le serveur après avoir tout chargé.
- **Les trois chiffres du haut portent sur toute la recherche**, pas sur les lignes affichées :
  ils sont additionnés par la base. Le premier s'appelle maintenant « Sorties trouvées » (au lieu
  de « Sorties affichées »).

Le résultat d'une recherche est le même qu'avant : produit, numéro de facture ou client, sans
tenir compte des majuscules.

## Détail

Côté serveur :

- `GET /api/stock/outputs` accepte `page` (0 par défaut) et `size` (50 par défaut, 200 au plus)
  et répond un objet au lieu d'une liste :
  `items`, `total`, `totalQuantity`, `totalValue`, `page`, `size`, `hasMore`.
- `StockMovementRepository.findOutputs` : une requête paginée, avec sa requête de comptage ;
  `sumOutputs` : les deux totaux.
- `StockService.likePattern` : les caractères `%`, `_` et `\` tapés dans la recherche sont
  cherchés tels quels. Avant ce correctif-là, la question ne se posait pas (filtre en Java) ; avec
  un `LIKE`, chercher « 50% » aurait tout renvoyé.
- La taille demandée est bornée : `size=100000` renvoie 200 lignes.

Côté écran (`stock-outputs.component.ts`) :

- les pages reçues s'ajoutent les unes sous les autres ; une nouvelle recherche repart de la
  première ;
- une réponse dépassée par une recherche plus récente est ignorée (frappe rapide) ;
- la liste se recharge toujours toute seule quand une commande change, **mais seulement tant
  qu'on est sur la première page** : la recharger sous quelqu'un qui lit plus bas le renverrait
  en haut.

Les droits ne changent pas : responsable dépôt et super-admin, comme décidé dans la partie 2.

Fichiers : `repository/StockMovementRepository.java`, `service/StockService.java`,
`web/StockController.java`, `web/dto/StockOutputPageResponse.java` (nouveau) ;
`ui/src/app/core/api/stock.api.ts`, `core/models/stock-movement.model.ts`,
`features/depot-manager/stock-outputs.component.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 514, tous verts** (`mvnw -o -DskipFrontend=true test`), dont
  5 nouveaux dans `StockOutputPagingTest` :
  - sept ventes lues trois par trois : trois pages, sans doublon ni oubli, la plus récente
    d'abord ;
  - les totaux d'une page de 2 lignes sont bien ceux des 7 ventes (28 unités, 42 000 Ar) ;
  - recherche sans tenir compte des majuscules, et par nom de client ;
  - `%` et `_` cherchés tels quels ;
  - taille démesurée bornée à 200.
- Les tests de droits de la partie 2 (`AccessRightsTest`), qui appellent cette même adresse,
  passent toujours.
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **L'écran n'a pas été ouvert dans un navigateur** : le bouton « Afficher plus » et les trois
  chiffres ont été compilés, pas vus.
- **Aucune mesure de vitesse** sur une grosse base : le gain est attendu (50 lignes lues au lieu
  de toutes), il n'a pas été chronométré. Les index sur les dates ne sont toujours pas créés
  (point séparé de `TODO.md`), ce qui comptera sur de très gros volumes.

## Pour essayer

1. Se connecter en responsable dépôt, ouvrir « Sorties ».
2. Vérifier « 50 sur N » sous le tableau, cliquer « Afficher plus ».
3. Taper un nom de produit : les trois chiffres du haut doivent suivre la recherche.

## Reste ouvert

- **Factures** : toujours chargées en entier. C'est le vrai morceau de la pagination, et il n'est
  pas mécanique, pour trois raisons :
  - les règles « qui lit quoi » de la partie 2 sont appliquées après lecture, en Java ; il faut
    les traduire en requête ;
  - les écrans de factures se mettent à jour en direct à partir de la liste complète qu'ils
    tiennent en mémoire ;
  - plusieurs totaux (chiffre du jour, à encaisser) sont additionnés par l'écran sur cette liste.

  Proposition pour la suite : borner l'historique par **période** (30 derniers jours par défaut,
  avec un choix de dates) plutôt que par pages. Les totaux restent justes pour la période
  affichée et la mise à jour en direct continue de fonctionner.
- **Approvisionnements, corrections de stock, produits** : listes complètes aussi, mais elles
  grossissent lentement (une ligne par livraison, par correction, par produit).
- **Statut de remise périmé au-delà de la première page** : si une commande change pendant qu'on
  lit la page 3, sa pastille ne se met à jour qu'à la prochaine recherche.
- **Ancienne application mobile** : un téléphone dont les pages n'ont pas encore été mises à jour
  attend l'ancienne forme de réponse sur cet écran. La mise à jour des pages se fait au lancement
  de l'application ; l'écran « Sorties » est de toute façon un écran de bureau.
- **Flyway** (l'autre moitié de la partie 6) : pas commencé.
