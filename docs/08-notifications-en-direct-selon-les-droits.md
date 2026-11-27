# Moussa Shop — notifications en direct selon les droits

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Les notifications en direct ignorent les
rôles ».

---

## Ce qui change

Les écrans se mettent à jour tout seuls : une vente faite à la caisse apparaît dans la file du
dépôt sans recharger la page. Pour cela le serveur « pousse » chaque commande modifiée vers les
écrans ouverts.

**Avant.**

- Chaque commande modifiée était poussée **à tous les comptes connectés**, entière : client,
  montant, vendeur, lignes. La partie 2 avait limité ce que chacun peut *demander* (un preneur de
  commandes ne lit que les siennes), mais pas ce que chacun *recevait* en direct : un preneur de
  commandes connecté recevait toutes les ventes de la boutique.
- Une connexion ouverte **restait ouverte** quand le compte était désactivé, changeait de rôle,
  changeait de mot de passe ou se déconnectait : elle continuait de recevoir jusqu'à la fermeture
  du navigateur.

**Après.**

- **Chaque compte reçoit sa part, et rien d'autre.** La règle est exactement celle des listes de
  factures (partie 2) :
  - super-admin et responsable dépôt : tout ;
  - caisse et magasinier : les commandes du jour et celles encore ouvertes, plus les leurs ;
  - preneur de commandes : les siennes seulement — il n'entend plus parler des autres.
- **Les bordereaux de versement** suivent leur propre règle, celle de l'écran : la caisse et le
  super-admin les reçoivent tous, les autres seulement ceux qu'ils ont déposés.
- **Les droits sont relus au moment de l'envoi**, sur le compte tel qu'il est maintenant, pas tel
  qu'il était à la connexion.
- **Une connexion est fermée dès que l'accès du compte change** : compte désactivé, rôles
  modifiés, mot de passe changé, déconnexion. Le navigateur se reconnecte tout seul ; il est
  accepté si sa session est toujours valable (avec les rôles du moment), refusé sinon.

Pour l'utilisateur honnête, rien ne se voit : les écrans continuent de se mettre à jour.

## Détail

Côté serveur :

- Les changements de commandes ne passent plus par le canal commun `/topic/orders` mais par une
  **file par compte**, `/user/queue/orders`. Le serveur ne peut livrer une file qu'à son compte :
  s'abonner à celle d'un autre est impossible.
- `InvoiceService.sharesOf` : pour chaque compte connecté, les commandes du message qu'il peut
  lire, avec la même règle que `searchFor` (`InvoiceAccess`). Un compte désactivé ou supprimé
  depuis sa connexion n'a aucune part.
- `OrderChangePublisher` : un message par compte.
  - preneur de commandes sans commande à lui dans le message : rien n'est envoyé ;
  - caisse ou magasinier dont une commande vient de **sortir** de ce qu'il lit (un vieux crédit
    réglé à une autre caisse) : un message vide, qui fait recharger ses listes — sinon son écran
    garderait la commande dans son ancien état ;
  - personne de connecté : aucune lecture en base.
- `NotificationService.sendToUserQueue` et `connectedUserIds` (nouveaux).
- `notification/SocketSessions.java` (nouveau) : garde les connexions ouvertes et ferme celles
  d'un compte sur l'événement `AccountAccessChangedEvent` (nouveau), publié par
  `UserAccountService` (désactivation, rôles modifiés, mot de passe) et `SessionRevocation`
  (déconnexion). Toutes les connexions du compte sont fermées ; celles dont la session tient
  toujours reviennent seules.
- Le canal du stock (`/topic/stock`) reste commun : le catalogue et ses quantités sont lus par
  tous les comptes.

Côté écran :

- `RealtimeService.mine(nom)` (nouveau) : l'abonnement à sa propre file ;
- `OrderEvents` écoute `mine('orders')` au lieu de `topic('orders')`. Le reste est inchangé : un
  message vide faisait déjà recharger les listes.

Fichiers : `notification/OrderChangePublisher.java`, `notification/NotificationService.java`,
`notification/SocketSessions.java` (nouveau), `notification/WebSocketConfiguration.java`,
`service/InvoiceService.java`, `service/AccountAccessChangedEvent.java` (nouveau),
`service/UserAccountService.java`, `security/SessionRevocation.java` ;
`ui/src/app/core/realtime/realtime.service.ts`, `core/realtime/order-events.service.ts`,
`core/models/order-change.model.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 527, tous verts**, dont 3 nouveaux dans
  `SaleNotificationWebSocketTest`, sur de vraies connexions WebSocket :
  - un preneur de commandes n'entend pas la vente d'un collègue (le super-admin, si), puis entend
    sa propre commande ;
  - plus rien n'est publié sur l'ancien canal commun ;
  - un compte désactivé perd sa connexion, et ne peut pas en rouvrir une avec le même jeton.
- Le test existant « la file du dépôt suit une vente et sa remise » passe sur la file par compte.
- **Tests automatiques de l'écran : 159, tous verts.**
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **Pas d'essai dans un navigateur** avec deux comptes ouverts côte à côte.
- **La reconnexion automatique du navigateur** après une fermeture par le serveur repose sur la
  bibliothèque STOMP déjà utilisée (délai de reconnexion existant) ; elle n'a pas de test
  automatique côté écran.
- **Fermeture sur changement de rôle, de mot de passe et sur déconnexion** : même mécanisme que
  la désactivation, qui seule a un test de bout en bout.

## Pour essayer

1. Ouvrir deux navigateurs : un preneur de commandes sur « Mes commandes », un caissier sur
   « Nouvelle vente ».
2. Faire une vente à la caisse : rien ne bouge chez le preneur de commandes.
3. En super-admin, désactiver le compte du preneur de commandes : au prochain clic, il est
   renvoyé à la connexion ; sa connexion en direct est déjà fermée.

## Reste ouvert

- **Ancienne application mobile** : un téléphone dont les pages ne sont pas encore à jour écoute
  l'ancien canal et ne reçoit plus les changements en direct jusqu'à sa mise à jour (faite au
  lancement). Ses listes restent justes au rechargement.
- **Une lecture par vente pour les comptes connectés** : pour chaque changement, le serveur relit
  les comptes connectés (une requête) et, pour ceux qui ne lisent pas tout, les commandes dont
  l'argent est passé par eux. Négligeable pour une boutique (quelques postes).
- **Le menu de l'écran après un changement de rôle** reste l'ancien jusqu'au rechargement de la
  page : point suivant de `TODO.md`.
- **Les cloches et messages ciblés par rôle** (`sendToRoles`) lisent les rôles de la connexion ;
  ils sont à jour puisque la connexion est rouverte à chaque changement de rôle.
