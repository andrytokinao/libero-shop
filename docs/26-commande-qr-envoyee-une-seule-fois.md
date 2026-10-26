# Moussa Shop — commande par QR code enregistrée une seule fois

Réalisée le 16 octobre 2026.

Point de `TODO.md`, section 3 : **P2 — Commande par QR code envoyée deux fois.** Les commandes
passées depuis une table n'envoyaient pas de référence de panier : une coupure pouvait encore en
créer deux.

---

## Ce qui change

**Avant.** Un client commande depuis le QR code de sa table. La commande est enregistrée, mais la
réponse se perd sur le Wi-Fi : son téléphone n'affiche rien, le panier est toujours rempli, il
appuie à nouveau sur « Confirmer la commande ».
- Dans les 30 secondes : refus « Votre commande précédente vient d'être envoyée », alors que le
  téléphone n'en sait rien et que le panier reste plein.
- Après 30 secondes : **une deuxième commande**, servie et décomptée du stock deux fois.

**Après.** Le téléphone nomme chaque commande avant de l'envoyer et garde ce nom tant que la
commande n'a pas reçu de réponse. Renvoyée, la commande déjà enregistrée est rendue telle quelle,
quel que soit le délai : le panier se vide, la commande apparaît dans « Vos commandes » (une seule
fois), le stock n'est décompté qu'une fois.

C'est le même mécanisme que la caisse (`REALISATION-partie-3-argent.md`) :
- changer le panier (un article, une quantité, le nom) avant de renvoyer donne une **nouvelle**
  commande ;
- une commande réussie libère le nom : la commande suivante, même identique, est une autre
  commande.

## Détail

- `PublicOrderRequest` : champ facultatif `clientReference` (40 caractères au plus). Un téléphone
  qui ne l'envoie pas (ancienne page en cache) fonctionne comme avant.
- `OnlineOrderService.order` : la référence est **liée à la table** (`table-<id>:<référence>`)
  avant d'être enregistrée sur la vente. Toutes les commandes en ligne sont signées par le même
  compte `commande-en-ligne` : sans la table, une référence reprise à une autre table rendrait la
  commande de cette autre table. Elle est cherchée **avant** la pause de 30 secondes entre deux
  commandes, pour qu'un renvoi ne soit pas refusé.
- `PublicOrderController` : deux envois simultanés de la même commande — le second, refusé par la
  base, est répondu avec la commande du premier (comme `SaleController`).
- `ui/.../sale-reference.ts` : `SaleReference` accepte un autre type de panier que celui de la
  caisse.
- `ui/.../table-order.component.ts` : envoie la référence, la libère après une réussite, n'ajoute
  pas deux fois la même commande à « Vos commandes ».

## Vérifications faites

- Deux tests serveur : un renvoi (dans les 30 secondes) rend la première commande et le stock
  n'est décompté qu'une fois ; la même référence à une autre table donne une commande propre à
  cette table.
- Tests serveur : 586, tous verts. Tests de l'interface : 185, tous verts ; `tsc` sans erreur.

## Non vérifié

- Pas essayé sur un téléphone : le paquet de production de l'interface ne se construit plus sur
  ce poste (`esbuild.exe` retiré de `node_modules`, voir
  `24-navigateur-apres-installation-et-sauvegardes-cachees.md`).
