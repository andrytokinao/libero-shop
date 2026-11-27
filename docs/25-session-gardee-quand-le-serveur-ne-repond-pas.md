# Moussa Shop — session gardée quand le serveur ne répond pas

Réalisée le 16 octobre 2026.

Point de `TODO.md`, section 7 : **P2 — Une coupure réseau au démarrage déconnecte l'utilisateur**
(`auth.service.ts` efface le jeton sur toute erreur, pas seulement 401/403).

---

## Ce qui change

**Avant.** À l'ouverture de l'application (ou au rechargement de la page), l'écran demande au
serveur qui est connecté. Si le serveur ne répondait pas — PC du serveur encore en train de
démarrer après une coupure de courant, Wi-Fi coupé, téléphone hors du réseau de la boutique — la
session était effacée : il fallait retaper identifiant et mot de passe une fois le serveur revenu.

**Après.**

- Seul un **refus du serveur** (401 ou 403 : session expirée, fermée, mot de passe changé)
  efface la session, comme avant.
- **Pas de réponse**, ou une réponse qui ne vient pas du serveur lui-même (502, 503, 504 d'un
  reverse proxy, erreur 500) : la session est **gardée**. La page de connexion s'affiche avec un
  encadré « Serveur injoignable — Votre session est conservée : elle reprendra dès que le serveur
  répondra » et un bouton « Réessayer maintenant ».
- La page réessaie **toute seule toutes les 5 secondes**. Dès que le serveur répond, l'utilisateur
  revient sur la page où il était (ou l'accueil de son rôle), sans rien taper.
- Si entre-temps la session a été fermée côté serveur, l'encadré disparaît et la connexion
  normale reprend.
- Se connecter avec un autre compte pendant ce temps reste possible : le nouveau compte remplace
  l'ancienne session.

## Détail

- `ui/src/app/core/services/auth.service.ts` : `refresh()` distingue un refus (401, 403 → jeton
  effacé) de tout le reste (jeton gardé, signal `unreachable` à vrai). La connexion, la
  déconnexion et une réponse du serveur remettent ce signal à faux.
- `ui/src/app/features/auth/login.component.ts` : encadré, bouton et nouvel essai toutes les
  5 secondes tant que le signal est vrai (pas pendant une connexion en cours).
- `ui/src/app/core/services/auth.service.spec.ts` : quatre tests (coupure réseau, proxy sans
  serveur, retour du serveur avec le jeton gardé, refus du jeton).

## Vérifications faites

- Tests de l'interface : 185, tous verts ; vérification des types (`tsc`) sans erreur.

## Non vérifié

- Pas essayé à l'écran : le paquet de production de l'interface ne se construit plus sur ce poste
  (`esbuild.exe` retiré de `node_modules`, voir
  `24-navigateur-apres-installation-et-sauvegardes-cachees.md`).

## Reste ouvert

- Une coupure **en plein travail** (pas au démarrage) n'est pas concernée : chaque écran affiche
  déjà « Serveur injoignable » et la session n'est pas touchée. La caisse hors ligne reste un
  point à part (`TODO.md`, section 7).
