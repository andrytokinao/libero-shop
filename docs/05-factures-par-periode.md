# Moussa Shop — factures lues par période

Deuxième tranche de la partie 6 (pagination). Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 5, « Factures chargées en entier ».

---

## Ce qui change

**Avant.** Chaque écran qui liste des factures (« Toutes les factures », « Mes factures »,
« Historique des remises », la file du dépôt…) demandait au serveur **toutes les factures depuis
le premier jour**, puis le serveur triait en mémoire. Au bout d'un an d'activité, ouvrir un de
ces écrans aurait fait lire des dizaines de milliers de factures pour en afficher quelques-unes.

**Après.**

- **Sans rien choisir, un écran reçoit les 30 derniers jours**, plus **toutes les factures encore
  ouvertes, quel que soit leur âge** : non payées, argent encaissé pas encore arrivé en caisse,
  marchandise pas encore remise. Un crédit vieux de trois mois reste donc dans « À encaisser » :
  c'est du travail à faire, pas de l'historique.
- **Pour remonter plus loin, on choisit des dates** : deux champs « Du » et « Au » sur
  « Toutes les factures », « Mes factures » et « Historique des remises ». Le bouton
  « 30 derniers jours » revient à l'affichage normal.
- **Les totaux affichés restent justes** : ils portent sur la période affichée, et le titre de
  l'écran dit laquelle (« 30 derniers jours et factures encore ouvertes » ou « période choisie »).
- **La mise à jour en direct continue de fonctionner** : une vente faite à la caisse apparaît
  toujours toute seule dans les listes concernées.

C'est le choix annoncé dans la tranche précédente : une **période** plutôt que des pages, parce
que les écrans de factures additionnent eux-mêmes leurs totaux et se mettent à jour en direct.

## Détail

Côté serveur :

- `GET /api/invoices` accepte `from` et `to` (jours `aaaa-mm-jj`, tous deux compris). Trois cas,
  dans cet ordre :
  1. `todayOnly=true` : la journée, comme avant ;
  2. `from` et/ou `to` : exactement ces jours-là, ouvertes ou non ;
  3. rien : les 30 derniers jours **ou** encore ouverte.
- `InvoiceService.HISTORY_DAYS = 30` : la durée par défaut, à un seul endroit côté serveur.
- `InvoiceRepository.search` : la borne de date et la règle « encore ouverte » sont dans la
  requête, plus en Java. « Ouverte » a le même sens que dans les droits de la partie 2
  (`InvoiceAccess.isOpen`) : paiement `UNPAID`, `COLLECTED` ou `REMITTED`, ou remise `PENDING`
  ou `IN_PROGRESS`.
- Une fin avant le début est refusée (`INVALID_PERIOD`).
- Les droits ne changent pas : la règle « qui lit quoi » s'applique toujours, sur la période lue.
- Les chiffres que le serveur calcule lui-même (tableaux de bord) passent par
  `InvoiceService.search`, qui n'est pas modifié.

Côté écran :

- `shared/components/period-filter.component.ts` (nouveau) : les deux champs de date et le
  bouton de retour. Une période saisie à l'envers n'est pas envoyée.
- `core/store/invoice-query.ts` : la même règle redite dans le navigateur, pour qu'une facture
  poussée en direct rejoigne ou quitte les bonnes listes. Par exemple, un vieux crédit payé
  aujourd'hui quitte la liste par défaut au moment du paiement, puisqu'il n'est plus ouvert et
  date de plus de 30 jours.
- « Historique des remises » : le chiffre « Total remis (toutes dates) » devient « Remises
  affichées », avec la période en dessous.

Fichiers : `repository/InvoiceRepository.java`, `service/InvoiceService.java`,
`web/InvoiceController.java` ; `ui/src/app/core/api/invoice.api.ts`,
`core/store/invoice-query.ts`, `core/store/invoice.store.ts`,
`shared/components/period-filter.component.ts` (nouveau),
`features/admin/all-invoices.component.ts`, `features/cashier/cashier-invoices.component.ts`,
`features/depot/delivery-history.component.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 521, tous verts** (`mvnw -o -DskipFrontend=true test`), dont
  5 nouveaux dans `InvoicePeriodTest` :
  - sans période : la facture du jour, le vieux crédit non payé et la vieille commande pas encore
    remise sont listés ; la vieille facture payée et remise ne l'est pas ;
  - avec une période : ses jours et rien d'autre, dernier jour compris ;
  - « aujourd'hui seulement » l'emporte sur la période par défaut ;
  - un filtre de statut s'ajoute à la période par défaut ;
  - une fin avant le début est refusée.
- Les tests de droits de la partie 2 (`AccessRightsTest`), qui appellent cette même adresse,
  passent toujours.
- **Tests automatiques de l'écran : 159, tous verts** (`ng test`, navigateur Edge sans fenêtre),
  dont 2 nouveaux sur la règle redite dans le navigateur.
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **Les écrans n'ont pas été ouverts dans un navigateur** : les champs « Du / Au » ont été
  compilés, pas vus ni cliqués.
- **Aucune mesure de vitesse** sur une grosse base. L'index sur la date des factures n'existe
  toujours pas (point séparé de `TODO.md`).

## Pour essayer

1. Se connecter en super-admin, ouvrir « Toutes les factures » : le titre indique
   « 30 derniers jours et factures encore ouvertes ».
2. Choisir une date dans « Du » : seules les factures à partir de ce jour s'affichent, et le
   bouton « 30 derniers jours » apparaît.
3. En caissier, ouvrir « À encaisser » : un crédit ancien doit toujours y figurer.

## Reste ouvert

- **Une période très longue recharge tout** : choisir « du 1er janvier à aujourd'hui » relit
  toute l'année. C'est un choix explicite de l'utilisateur, plus le comportement par défaut ; une
  limite (un an ?) pourrait être ajoutée.
- **La recherche et les droits restent appliqués en Java**, mais sur la période lue seulement.
- **La file du dépôt en vue « Toutes »** suit maintenant la règle par défaut (30 jours et
  ouvertes), sans champ de dates : c'est un écran de travail, pas d'historique.
- **Ancienne application mobile** : un téléphone pas encore mis à jour reçoit simplement
  30 jours au lieu de tout ; rien ne casse.
- **Tableaux de bord** : ils lisent encore des listes entières pour faire leurs comptes — c'est
  le point « Statistiques calculées en mémoire » de `TODO.md`.
- **Flyway** : pas commencé.
