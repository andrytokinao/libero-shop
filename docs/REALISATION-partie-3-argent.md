# Libero Shop — réalisation, partie 3 : argent

Doubles paiements, bordereaux en double et vente en double. Réalisée le 10 octobre 2026.

Points de [`TODO.md`](TODO.md) couverts : section 3, « Paiements et remises en double » et
« Vente en double si le réseau coupe à l'encaissement ».

Les parties 1 et 2 sont dans [`REALISATION.md`](REALISATION.md).

---

## Ce qui change

**Avant.** Trois situations pouvaient écrire le même argent deux fois :

1. deux caisses encaissent la même commande au même instant : deux paiements pour une facture ;
2. le magasinier encaisse à la remise pendant que la caisse encaisse : deux paiements aussi ;
3. le Wi-Fi coupe juste après « Encaisser » : la vente est enregistrée, l'écran dit que le
   serveur n'a pas répondu, le caissier appuie de nouveau, et une deuxième facture sort avec une
   deuxième sortie de stock.

Un double appui sur « Verser » pouvait de la même façon créer deux bordereaux pour la même somme.

**Après.**

- **Le premier arrivé gagne, le second est refusé sans rien écrire.** Il reçoit soit la règle
  habituelle (« La commande F-1007 est déjà réglée »), soit, s'ils sont arrivés au même instant,
  le message : « Quelqu'un d'autre vient de modifier cette commande au même moment. Rien n'a été
  enregistré de votre côté : vérifiez son état avant de recommencer. »
- **Un panier envoyé deux fois est une seule vente.** Le second envoi reçoit la facture du
  premier ; le stock ne bouge qu'une fois.

Rien ne change à l'écran en fonctionnement normal.

## 1. Paiements et bordereaux : compteur de modifications

`Invoice` et `CashRemittance` portent maintenant un compteur (`@Version`). Chaque modification
l'augmente ; une écriture décidée sur un compteur périmé est refusée par la base, avec tout ce
qu'elle allait écrire — le paiement compris, puisqu'il est dans la même transaction.

| Situation | Ce qui la bloque |
|---|---|
| deux `pay` sur la même commande | le compteur de la commande |
| `pay` à la caisse et encaissement à la remise (`deliver`) | le compteur de la commande |
| deux versements du même argent | le compteur des commandes que le bordereau fait passer à « versé » |
| deux confirmations du même bordereau | le compteur du bordereau |

Le refus arrive à l'écran en **409 `CONCURRENT_UPDATE`**, ajouté à `ApiExceptionHandler` : même
statut que les autres règles métier, donc affiché par le mécanisme existant.

Les services (`InvoiceService`, `RemittanceService`) n'ont pas changé : c'est la base qui arbitre.

**Bases existantes.** Les deux colonnes `version` sont créées avec la valeur 0 par défaut
(`@ColumnDefault("0")`), donc les commandes et bordereaux déjà enregistrés restent utilisables.

Fichiers : `entity/Invoice.java`, `entity/CashRemittance.java`, `web/ApiExceptionHandler.java`.

## 2. Vente en double : référence de panier

La caisse donne un nom au panier avant de l'envoyer, et renvoie le même nom à chaque nouvelle
tentative. Le serveur garde ce nom sur la vente (`Sale.clientReference`, colonne unique).

Côté serveur (`SaleService.checkout`) :

- les produits sont verrouillés **avant** de chercher la référence : de deux envois simultanés du
  même panier, le second attend que le premier ait fini, puis trouve sa vente ;
- si la référence existe déjà, la facture d'origine est renvoyée et rien n'est écrit ;
- si deux envois passent malgré tout en même temps, la contrainte d'unicité de la base refuse le
  second, et `SaleController` répond avec la vente du premier ;
- **une référence n'ouvre que ses propres ventes** : la même référence présentée par un autre
  vendeur est refusée (`SALE_REFERENCE_TAKEN`) plutôt que de lui montrer la facture d'un collègue ;
- un client qui n'envoie pas de référence est servi comme avant : chaque envoi est une vente.

Côté écran (`core/sale/sale-reference.ts`, nouveau) :

- la référence est tirée au hasard (128 bits) au premier envoi d'un panier ;
- **elle appartient au panier tel qu'il est** : si une ligne, le client ou le mode de paiement
  change, l'envoi suivant en reçoit une nouvelle. Sans cela, un article ajouté après une coupure
  serait renvoyé sous l'ancienne référence, le serveur répondrait avec la première version de la
  vente, et l'article sortirait du magasin sans facture ;
- après une vente réussie, la référence est renouvelée : le même client qui rachète la même
  chose fait bien une deuxième vente.

Branché sur les deux écrans qui vendent : la caisse (`new-sale.component.ts`) et la prise de
commande sur téléphone (`order-taking.component.ts`).

Fichiers : `entity/Sale.java`, `service/SaleService.java`, `web/SaleController.java`,
`web/dto/CreateSaleRequest.java`, `repository/InvoiceRepository.java` ;
`ui/src/app/core/sale/sale-reference.ts` (+ `.spec.ts`), `core/models/api.model.ts`,
`features/cashier/new-sale.component.ts`, `features/orders/order-taking.component.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 509, tous verts** (`mvnw -o -DskipFrontend=true test`).
- **`ConcurrentMoneyTest` (5 nouveaux tests)** lance chaque situation 12 fois, les deux appels
  libérés au même instant :
  - deux caisses sur la même commande : toujours un seul paiement ;
  - magasinier et caisse : toujours un seul paiement ;
  - le même argent versé deux fois : toujours un seul bordereau ;
  - une vente renvoyée après coupure : même facture, stock diminué une fois ;
  - la même vente envoyée deux fois au même instant : une seule vente, les deux envois la
    reçoivent.

  La classe a été exécutée trois fois de suite, sans échec. Les perdants de chaque course ont
  tous été refusés par le compteur ou par une règle métier, jamais autrement.
- **Application réelle sur une copie d'une ancienne base** (`backend/data/libertyshop.mv.db`,
  schéma du 1ᵉʳ octobre, copiée dans un dossier temporaire ; l'original n'a pas été touché) :
  - le démarrage ajoute les colonnes sans erreur ;
  - une ancienne facture impayée (F-1007) s'encaisse, et un second encaissement répond 409
    « déjà réglée » ;
  - deux `POST /api/sales` avec la même référence renvoient la même facture (F-1033), et le stock
    du produit passe de 55 à 54, pas à 53.
- **Interface** : `ng build` passe, et les tests (`.spec.ts`) compilent.

## Non vérifié

- **Le test `sale-reference.spec.ts` n'a pas pu être exécuté** : Chrome sans écran ne démarre pas
  sur ce poste (`Cannot start ChromeHeadless`). Il compile ; à lancer avec `ng test`.
- **Aucun essai à l'écran** : la coupure de réseau suivie d'un second appui n'a pas été rejouée
  dans un navigateur. Le comportement du serveur dans ce cas est testé ; le fait que l'écran
  renvoie bien la même référence repose sur le code relu et sur le test non exécuté ci-dessus.
- **Message de conflit à l'écran** : il passe par l'affichage existant des erreurs 409 ; il n'a
  pas été vu dans un navigateur.

## Pour essayer

1. Ouvrir la caisse, préparer une vente, ouvrir les outils du navigateur (onglet Réseau) et
   passer « Hors ligne » **juste après** avoir cliqué sur « Encaisser » — ou plus simplement,
   arrêter le serveur après le clic puis le relancer.
2. Cliquer de nouveau sur « Encaisser » : une seule facture doit exister dans « Ventes du jour ».
3. Sur deux navigateurs, ouvrir la même commande impayée dans « À encaisser » et encaisser des
   deux côtés : le second reçoit un message, la commande ne porte qu'un paiement.

## Reste ouvert

- **Commandes passées par QR code depuis une table** (`/api/public/...`) : elles n'envoient pas
  de référence. Un client qui valide deux fois sur son téléphone après une coupure peut encore
  créer deux commandes.
- **Après un conflit, l'écran ne se recharge pas tout seul** : le message demande de vérifier ;
  la liste se met à jour par les notifications en direct, comme avant.
- **Un panier modifié après une coupure reste une deuxième vente** (voir plus haut pourquoi la
  référence change). Le caissier doit alors annuler la première dans « Ventes du jour ». C'est le
  comportement d'avant, limité à ce seul cas.
- **Les références restent en base** avec leur vente ; rien ne les efface, et rien n'en a besoin.
