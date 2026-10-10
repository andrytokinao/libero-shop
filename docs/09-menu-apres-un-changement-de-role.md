# Libero Shop — le menu suit un changement de rôle

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Menu après un changement de rôle ».

---

## Ce qui change

**Avant.** Quand le super-admin changeait les rôles d'un compte, le serveur appliquait le
changement tout de suite (partie 2), mais **l'écran déjà ouvert de la personne gardait l'ancien
menu** jusqu'au rechargement de la page. Elle voyait encore des entrées qui répondaient « votre
rôle ne vous autorise pas », et pas les nouvelles.

**Après.** L'écran suit en quelques secondes, sans rien recharger :

- **le menu devient celui des nouveaux rôles** ;
- si la personne est sur un écran qui **n'est plus le sien** (un caissier à qui on retire la
  caisse, resté sur « Nouvelle vente »), elle est ramenée à **l'accueil de son nouveau rôle** ;
- si le compte est **désactivé**, ou sa session fermée (mot de passe changé par
  l'administrateur), l'écran revient à **la page de connexion** tout de suite, au lieu d'attendre
  le prochain clic.

## Détail

Le point précédent ([`08-notifications-en-direct-selon-les-droits.md`](08-notifications-en-direct-selon-les-droits.md))
fait fermer par le serveur la connexion en direct d'un compte dès que son accès change. Cette
fermeture sert maintenant de signal à l'écran :

- `core/realtime/realtime.service.ts` : quand le serveur ferme la connexion avec le code réservé
  à ce cas (1008, « accès modifié »), l'écran redemande sa session. Une connexion simplement
  perdue (Wi-Fi coupé) ne déclenche rien de plus que la reconnexion habituelle.
- `core/services/auth.service.ts`, `reloadAfterAccessChange()` (nouveau) : relit
  `GET /api/auth/session`, d'où viennent les rôles et donc le menu ; puis décide : rester, aller
  à l'accueil du rôle, ou aller à la connexion.

Rien ne change côté serveur dans ce point.

Fichiers : `ui/src/app/core/services/auth.service.ts`,
`core/realtime/realtime.service.ts`, `core/services/auth.service.spec.ts` (nouveau).

## Vérifications faites

- **Tests automatiques de l'écran : 163, tous verts**, dont 4 nouveaux dans
  `auth.service.spec.ts` :
  - un super-admin devenu caissier reçoit le menu de la caisse et quitte l'écran
    d'administration pour l'accueil de la caisse ;
  - rien ne bouge si l'écran ouvert appartient toujours au compte ;
  - un compte désactivé (réponse 401) est renvoyé à la connexion, son jeton effacé ;
  - une session fermée (réponse « anonyme ») aussi.
- **Interface** : `ng build` passe sans erreur.
- Côté serveur, la fermeture de la connexion est couverte par le test du point précédent ; aucun
  fichier serveur n'est modifié ici (dernière passe complète : 527 tests verts).

## Non vérifié

- **Pas d'essai dans un navigateur** avec deux comptes : le lien entre la fermeture réelle de la
  connexion par le serveur et le code 1008 reçu par le navigateur n'a pas été observé à l'écran.
  Les deux moitiés sont testées chacune de son côté.
- **Application mobile** : même code, non essayé sur un téléphone.

## Pour essayer

1. Ouvrir deux navigateurs : un caissier sur « Nouvelle vente », le super-admin sur
   « Utilisateurs ».
2. Retirer le rôle caisse du premier et lui donner « preneur de commandes » : son menu change et
   il arrive sur l'accueil des commandes, sans toucher à sa page.
3. Désactiver le compte : son écran revient à la connexion.

## Reste ouvert

- **Si la connexion en direct n'est pas établie** au moment du changement (serveur injoignable
  quelques secondes), l'écran ne reçoit pas le signal : le menu suivra au prochain rechargement,
  et le serveur refuse de toute façon ce qui n'est plus permis.
- **Un panier en cours** sur l'écran quitté est perdu : c'est le point « Le panier en cours
  n'existe qu'en mémoire » de la section 7.
