# Libero Shop — navigateur ouvert après l'installation, page des sauvegardes cachée

Réalisée le 16 octobre 2026.

Demande directe, hors `TODO.md` :

1. une fois l'application installée et lancée avec succès, ouvrir la boutique dans le navigateur —
   seulement après l'installation, pas à chaque démarrage ;
2. la gestion des sauvegardes ne doit plus apparaître dans le menu, pour personne, administrateur
   compris, mais rester accessible à l'administrateur par une adresse.

---

## Ce qui change

**Navigateur après l'installation.**

- **Avant.** À la fin de l'installation, le serveur démarrait et l'icône apparaissait dans la zone
  de notification ; il fallait ensuite cliquer sur « Ouvrir Libero Shop » ou double-cliquer
  l'icône pour voir la boutique.
- **Après.** À la fin de l'installation, dès que le serveur est prêt (icône verte), la boutique
  s'ouvre d'elle-même dans le navigateur par défaut, sur la page de connexion.
- **Ne l'ouvre pas** : le démarrage à l'ouverture de session Windows, le raccourci du bureau ou du
  menu Démarrer, un « Redémarrer » depuis l'icône, le redémarrage après une restauration de
  sauvegarde ou une mise à jour par la page cachée `/admin/mise-a-jour`.
- Si le premier démarrage échoue (icône rouge), le navigateur s'ouvre quand « Démarrer » réussit
  ensuite, une seule fois.
- Une nouvelle version installée avec l'installateur (menu « Mettre à jour… » de l'icône) est une
  installation : le navigateur s'ouvre aussi à la fin.

**Sauvegardes.**

- **Avant.** « Sauvegardes » figurait dans le menu Admin.
- **Après.** L'entrée est retirée du menu. La page reste à l'adresse **`/admin/sauvegardes`**
  (par exemple `http://<adresse du serveur>:8080/admin/sauvegardes`), à taper dans la barre
  d'adresse, comme `/admin/mise-a-jour` et `/admin/licence`. Elle reste réservée au compte
  administrateur : un autre rôle qui tape l'adresse est renvoyé vers son propre accueil, et le
  serveur refuse ses appels aux sauvegardes comme avant.

Les sauvegardes automatiques continuent de tourner : seul l'accès à l'écran change.

## Détail

- `backend/src/main/jpackage/windows/main.wxs` : l'action de fin d'installation lance le
  programme avec l'argument `--liberoshop.open-browser`. La clé `Run` (ouverture de session) n'a
  pas d'argument.
- `BackendApplication` : repère cet argument, le retire avant de démarrer Spring (il n'est donc
  pas non plus repris lors d'un redémarrage après restauration), et le transmet à l'icône.
- `desktop/ServerTray` : `launch(starter, openWhenRunning)` ; à la première fois où le serveur
  passe « En marche », ouvre l'adresse affichée par l'icône, puis oublie la consigne.
- `ui/src/app/core/config/navigation.ts` : entrée « Sauvegardes » retirée du menu Admin.
- `ui/src/app/app.routes.ts` : route `/admin/sauvegardes` inchangée, commentée comme cachée.

## Vérifications faites

- Tests serveur : 584, tous verts. Tests de l'interface : 181, tous verts. Vérification des
  types de l'interface (`tsc`) sans erreur.

## Non vérifié

- **L'installateur n'a pas été reconstruit ni installé** (même raison que dans
  `14-lancement-automatique-apres-installation.md` : poste de développement signalé infecté).
  L'ouverture du navigateur à la fin d'une vraie installation n'a donc pas été vue à l'écran.
- **`ng build` ne tourne plus sur ce poste** : `esbuild.exe` a disparu de
  `ui/node_modules/@esbuild/win32-x64` (vraisemblablement retiré par l'antivirus). Les tests de
  l'interface passent, mais le paquet de production n'a pas été reconstruit. Sur un poste sain,
  `npm ci` dans `ui` le rétablit.

## Pour essayer

Sur un poste sain : construire l'installateur Windows, l'installer. À la fin, l'icône passe à
l'orange puis au vert, et le navigateur s'ouvre sur la boutique. Fermer puis rouvrir la session
Windows : l'icône revient, le navigateur ne s'ouvre pas. Se connecter en administrateur : le menu
n'a plus « Sauvegardes » ; taper `/admin/sauvegardes` après l'adresse du serveur pour y accéder.
