# Moussa Shop — mot de passe administrateur perdu

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Mot de passe administrateur perdu ».

> **Modifié le 11 octobre 2026** : la page n'affiche plus l'emplacement du fichier du code, c'est
> le support qui l'indique. Voir
> [`16-mot-de-passe-oublie-avec-le-support.md`](16-mot-de-passe-oublie-avec-le-support.md).
>
> **Modifié le 13 octobre 2026** : le lien de la page de connexion est retiré, le support donne
> l'adresse de la page. Voir
> [`19-mot-de-passe-oublie-lien-retire.md`](19-mot-de-passe-oublie-lien-retire.md).

---

## Ce qui change

**Avant.** Si le seul administrateur (super-admin) oubliait son mot de passe, plus personne ne
pouvait ouvrir de compte, installer une licence ou restaurer une sauvegarde. Aucune sortie
prévue : il fallait intervenir à la main dans la base.

**Après.** La page de connexion propose un lien **« Mot de passe administrateur oublié ? »**.
La procédure tient en deux étapes et **ne fonctionne que sur l'ordinateur où Moussa Shop est
installé** :

1. On clique sur « Commencer ». Le serveur écrit un **code à 8 chiffres dans un fichier de cet
   ordinateur** et affiche l'emplacement du fichier.
2. On ouvre le fichier (Bloc-notes), on tape le code, l'identifiant de l'administrateur et le
   nouveau mot de passe. On est connecté aussitôt.

Le fichier contient aussi **la liste des identifiants administrateur**, pour celui qui aurait
oublié l'identifiant en plus du mot de passe.

Ce qui protège la procédure :

- **Depuis un téléphone ou un autre poste, rien ne se passe** : la page explique seulement où
  aller. Même avec le bon code, lu par-dessus une épaule, la demande est refusée ailleurs que sur
  le serveur.
- **Il faut pouvoir lire un fichier sur le disque du serveur** : c'est la preuve demandée, à la
  place du mot de passe perdu.
- Le code est valable **15 minutes**, **une seule fois**, et **cinq mauvais essais l'annulent**.
  Un redémarrage du serveur l'annule aussi.
- Seuls les comptes **administrateur actifs** sont concernés. Les autres comptes (caisse, dépôt…)
  reçoivent leur mot de passe de l'administrateur, comme avant.
- Le nouveau mot de passe fait **8 caractères au moins**, et **toutes les sessions ouvertes du
  compte sont fermées**, comme pour tout changement de mot de passe.
- Chaque démarrage de procédure et chaque mot de passe changé sont écrits dans le journal du
  serveur.

## Détail

Côté serveur :

- `POST /api/setup/recovery/code` : tire le code, écrit le fichier, répond `file` (chemin
  complet) et `minutes`.
- `POST /api/setup/recovery/password` avec `code`, `username`, `password` : change le mot de
  passe.
- Les deux adresses sont publiques (personne n'est connecté) mais refusées hors de l'ordinateur
  du serveur, avec la même règle que la création du premier compte : adresse locale et aucun
  en-tête de relais (`RECOVERY_ONLY_ON_SERVER`).
- `service/AdminRecoveryService.java` (nouveau) : le code vit en mémoire, comparé en temps
  constant. Codes d'erreur : `RECOVERY_CODE_WRONG`, `RECOVERY_CODE_EXPIRED`,
  `RECOVERY_NOT_ADMIN` (qui laisse le code utilisable), `RECOVERY_NO_ADMIN`,
  `RECOVERY_UNAVAILABLE` (fichier impossible à écrire).
- Emplacement du fichier, réglage `liberoshop.security.recovery.code-file` :
  - en développement : `./data/recovery-code.txt` ;
  - application installée : `~/.libertyshop/security/recovery-code.txt`, à côté de la clé de
    signature (`backend/src/main/jpackage/runtime.args`).

Côté écran :

- `features/auth/admin-recovery.component.ts` (nouveau), page `/secours` ;
- lien sur la page de connexion (pas dans l'application mobile : un téléphone n'est jamais
  l'ordinateur du serveur) ;
- `core/api/setup.api.ts` : les deux appels.

Fichiers : `service/AdminRecoveryService.java` (nouveau), `web/SetupController.java`,
`web/dto/RecoverAdminRequest.java` (nouveau), `security/SecurityConfiguration.java`,
`resources/application.properties`, `jpackage/runtime.args` ;
`ui/src/app/features/auth/admin-recovery.component.ts` (nouveau),
`features/auth/login.component.ts`, `core/api/setup.api.ts`, `app.routes.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 524, tous verts**, dont 3 nouveaux dans
  `AdminRecoveryTest` :
  - sur le serveur, avec le code du fichier : mauvais code refusé, compte de caisse refusé,
    mot de passe de 6 caractères refusé, puis nouveau mot de passe accepté ; l'ancien ne connecte
    plus, le fichier est supprimé, le code ne sert pas deux fois ;
  - depuis un téléphone ou à travers un relais : ni code, ni nouveau mot de passe, même avec le
    bon code ;
  - cinq mauvais codes annulent le bon.
- **Tests automatiques de l'écran : 159, tous verts.**
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **La page n'a pas été ouverte dans un navigateur**, et la procédure n'a pas été faite sur une
  application installée (emplacement réel du fichier sous Windows).
- **L'expiration après 15 minutes** n'a pas de test automatique (l'annulation après cinq essais
  et après usage en ont un).

## Pour essayer

1. Sur l'ordinateur du serveur, se déconnecter, cliquer « Mot de passe administrateur oublié ? ».
2. « Commencer », ouvrir le fichier indiqué, taper le code, l'identifiant et un nouveau mot de
   passe.
3. Refaire l'étape 1 depuis un téléphone : la page doit seulement expliquer où aller.

## Reste ouvert

- **Qui s'assoit devant l'ordinateur du serveur peut refaire la procédure.** Dans une petite
  boutique, c'est souvent le poste de caisse. C'est le prix d'une sortie de secours sans
  intervention de l'éditeur ; la parade est une session Windows protégée par mot de passe pour le
  poste du serveur, et le journal du serveur garde la trace de chaque usage. Une variante plus
  stricte (code fourni par l'éditeur, comme pour la licence) reste possible.
- **Aucune alerte à l'écran** pour les autres administrateurs quand la procédure a servi : la
  trace est dans le journal seulement. À relier au point « Piste d'audit » de `TODO.md`.
- **Changer son propre mot de passe** depuis son compte : toujours pas possible, c'est le point
  « Mots de passe » de la section 1.
