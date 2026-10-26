# Moussa Shop — la caisse est prévenue des commandes à encaisser

Réalisée le 13 octobre 2026.

Demande : « si le vendeur vend, qui est prévenu de la nouvelle vente ? On ne la voit pas
s'afficher chez le caissier et l'agent de dépôt. »

---

## Qui est prévenu d'une vente

Deux choses différentes arrivent sur les écrans quand une vente est enregistrée.

**1. Les listes se mettent à jour toutes seules** (sans cloche, sans message) :

| Écran | Ce qui apparaît |
|---|---|
| Caisse, « À encaisser » | toute commande non payée, quel que soit le vendeur |
| Dépôt, « Remise » | toute commande à remettre |
| Preneur de commandes, « Mes commandes » | les siennes seulement |
| Super-admin, responsable dépôt | tout |

**2. La cloche et le message en bas d'écran** préviennent une personne qu'elle a quelque chose à
faire :

| Événement | Qui est prévenu | Condition |
|---|---|---|
| Nouvelle vente à préparer | magasiniers et responsable dépôt | réglage « remise séparée » activé |
| **Nouvelle commande à encaisser** (nouveau) | **les caissiers** | la commande n'est pas payée |
| Commande remise | le vendeur de la commande | — |
| Versement à confirmer | les caissiers | — |
| Versement confirmé | le magasinier qui a versé | — |

Dans tous les cas, **celui qui vient de faire la vente n'est pas prévenu de sa propre vente**.

## Ce qui change

**Avant.** La caisse n'était jamais prévenue d'une vente. Une commande prise par un serveur ou un
réceptionniste et laissée impayée arrivait bien dans « À encaisser », mais sans cloche ni
message : le caissier ne la voyait que s'il regardait cet écran.

**Après.** Dès qu'une commande est enregistrée **non payée**, chaque caissier connecté reçoit
« Nouvelle commande à encaisser », avec le numéro, le client, le montant et le nom de celui qui
l'a prise. Un clic sur la notification ouvre « À encaisser ».

Ce qui ne change pas :

- **Une vente payée à la caisse n'est pas annoncée aux autres caisses** : elles n'ont rien à
  faire.
- **Le dépôt n'est prévenu que si la « remise séparée » est activée** (Paramètres de la
  boutique). Sans ce réglage, le client repart avec sa marchandise au moment de la vente : il n'y
  a rien à préparer, donc rien à annoncer au magasinier.

## Si rien ne s'affiche chez le magasinier

À vérifier dans cet ordre :

1. **Paramètres de la boutique → « remise séparée »** doit être activé. C'est la cause la plus
   probable.
2. La vente doit être faite **par un autre compte** que celui qui regarde.
3. Le point en haut de l'écran (état de la connexion en direct) doit être vert. Hors ligne, la
   liste se remet à jour au retour de la connexion, mais la cloche de ce qui a été manqué ne
   revient pas.
4. Un compte dont on vient de changer le rôle doit avoir le bon rôle : « agent de dépôt » pour
   la remise, « caisse » pour l'encaissement.

## Détail

Côté serveur :

- `notification/SaleNotifier.java` : deux envois au lieu d'un. Au dépôt (`DEPOT`) si la remise
  est séparée, comme avant ; à la caisse (`DESK`) si la commande est `UNPAID`. Le vendeur est
  exclu des deux.
- `notification/NotificationType.java` : nouveau type `ORDER_TO_COLLECT`.
- Quand le dépôt encaisse à la remise (« paiement au dépôt »), le message dit « à encaisser à la
  caisse ou à la remise » : le client peut encore choisir de payer à la caisse.

Côté écran :

- `core/models/notification.model.ts` : type `ORDER_TO_COLLECT`.
- `core/realtime/notification-center.service.ts` : la notification mène à `/caisse/a-encaisser`.

## Vérifications faites

- **Tests automatiques du serveur : 523, tous verts**, dont, sur de vraies connexions WebSocket
  (`SaleNotificationWebSocketTest`, 11 tests) :
  - une vente impayée prévient le magasinier (« à préparer ») **et** l'autre caisse
    (« à encaisser », avec le montant) ; le vendeur ne reçoit rien ;
  - une vente payée prévient le magasinier et **pas** l'autre caisse (nouveau test).
- **Tests automatiques de l'écran : 164, tous verts**, dont 1 nouveau (la notification mène le
  caissier à « À encaisser »).
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **Pas d'essai dans un navigateur** avec deux comptes ouverts côte à côte.
- La cause exacte de ce qui a été observé chez le magasinier n'a pas été reproduite : les tests
  montrent que le magasinier est prévenu quand la remise séparée est activée. Voir « Si rien ne
  s'affiche chez le magasinier ».

## Pour essayer

1. Ouvrir deux navigateurs : un preneur de commandes (ou un caissier), et un autre caissier.
2. Enregistrer une commande **non payée** sur le premier.
3. Sur le second : la cloche compte 1, le message « Nouvelle commande à encaisser » s'affiche, et
   la commande est dans « À encaisser ».
4. Enregistrer une vente **payée** : le second caissier ne reçoit rien.

## Reste ouvert

- **Un compte à la fois caissier et magasinier** reçoit deux notifications pour une commande
  impayée d'un collègue : une à préparer, une à encaisser. Ce sont deux tâches ; à regrouper si
  cela gêne.
- **Rien n'est gardé pour un écran fermé** : la cloche ne montre que ce qui est arrivé pendant
  que l'écran était ouvert. Les listes, elles, sont toujours justes.
- **Argent pris par le preneur de commandes** (« la prise de commande peut encaisser ») : la
  caisse n'est prévenue qu'au versement, comme pour le dépôt.
