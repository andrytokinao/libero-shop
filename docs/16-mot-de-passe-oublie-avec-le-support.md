# Moussa Shop — mot de passe administrateur oublié : avec le support

Réalisée le 11 octobre 2026.

Demande directe, qui modifie la procédure de
[`06-mot-de-passe-administrateur-perdu.md`](06-mot-de-passe-administrateur-perdu.md).

---

## Ce qui change

**Avant.** Le lien « Mot de passe administrateur oublié ? » est sur la page de connexion, visible
de tous. Après « Commencer », la page affichait **l'emplacement complet du fichier** contenant le
code de secours. Quiconque s'asseyait devant l'ordinateur du serveur — souvent le poste de
caisse — pouvait donc trouver le bouton, lire le fichier et se donner le mot de passe de
l'administrateur.

**Après.**

1. **La page ne dit plus où est le fichier du code.** Elle indique seulement qu'un code vient
   d'être écrit sur cet ordinateur et qu'il faut **appeler le support de Moussa Shop**, avec son
   numéro (WhatsApp).
2. **Le support indique où lire le code**, après s'être assuré de parler à la bonne personne.
3. Le serveur ne renvoie plus cet emplacement du tout : on ne le retrouve pas non plus en
   regardant les échanges du navigateur.

Le reste de la procédure est inchangé : uniquement sur l'ordinateur du serveur, code valable
15 minutes, une seule fois, annulé après cinq mauvais essais.

## Pour le support : où est le code

Sur l'ordinateur où Moussa Shop est installé, dans le dossier de l'utilisateur Windows :

```
C:\Users\<nom de la session>\.libertyshop\security\recovery-code.txt
```

Le plus simple à dicter : touches **Windows + R**, taper
`%USERPROFILE%\.libertyshop\security\recovery-code.txt`, Entrée. Le fichier s'ouvre dans le
Bloc-notes ; il contient le code et les identifiants des administrateurs. Il n'existe qu'après
avoir cliqué « Commencer » et disparaît une fois le mot de passe changé.

L'emplacement est aussi écrit dans le journal du serveur à chaque démarrage de la procédure.

## Détail

- `service/AdminRecoveryService.java` : `start()` ne renvoie plus que la durée de validité.
- `web/SetupController.java` : la réponse de `POST /api/setup/recovery/code` ne contient plus
  `file`.
- `features/auth/admin-recovery.component.ts` : textes des deux étapes, numéro du support.
- `core/config/publisher.ts` : le numéro du support (`SUPPORT`), jusqu'ici écrit dans la page de
  connexion seulement ; `features/auth/login.component.ts` l'utilise.

Fichiers : `service/AdminRecoveryService.java`, `web/SetupController.java`, test
`web/AdminRecoveryTest.java` ; `ui/src/app/features/auth/admin-recovery.component.ts`,
`features/auth/login.component.ts`, `core/api/setup.api.ts`, `core/config/publisher.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 544, tous verts.** `AdminRecoveryTest` vérifie désormais que
  la réponse ne contient pas l'emplacement du fichier.
- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe.

## Non vérifié

- **La page n'a pas été ouverte dans un navigateur.**

## Reste ouvert — à décider

- **C'est une protection par discrétion.** L'emplacement est le même sur toutes les
  installations : quelqu'un qui l'a appris une fois (un ancien employé, ce document) peut refaire
  la procédure sans appeler. Pour que l'appel au support soit **obligatoire**, il faudrait que le
  support fournisse lui-même un code que le logiciel vérifie (comme pour la licence), au lieu
  d'indiquer un fichier. À faire si le risque le justifie.
- La session Windows du poste serveur protégée par un mot de passe reste la meilleure parade.
- ~~Le lien reste visible sur la page de connexion~~ — retiré le 13 octobre 2026, voir
  [`19-mot-de-passe-oublie-lien-retire.md`](19-mot-de-passe-oublie-lien-retire.md).
