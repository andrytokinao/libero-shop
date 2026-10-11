# Libero Shop — lancement automatique après installation

Réalisée le 11 octobre 2026.

Demande directe, hors `TODO.md` : qu'une fois installée sur Windows, l'application se lance
toute seule.

---

## Ce qui change

**Avant.** L'installateur Windows posait l'application, son raccourci sur le bureau et son entrée
dans le menu Démarrer, puis s'arrêtait là. Il fallait cliquer sur le raccourci pour démarrer le
serveur, et recommencer à chaque fois que l'ordinateur était rallumé.

**Après.**

1. **L'application démarre dès la fin de l'installation.** L'icône apparaît dans la zone de
   notification sans rien cliquer. C'est vrai pour une première installation comme pour une mise
   à jour : après « Mettre à jour… » dans le menu de l'icône, la nouvelle version redémarre seule.
2. **Elle démarre à chaque ouverture de session Windows.** Le commerçant allume l'ordinateur, le
   serveur est prêt.
3. **La désinstallation retire ce démarrage automatique** avec le reste.

Le raccourci du bureau et l'entrée du menu Démarrer existaient déjà et ne changent pas.

Pour **désactiver** le démarrage avec Windows sur un poste : Gestionnaire des tâches → onglet
« Applications de démarrage » → Libero Shop → Désactiver.

## Détail

- `backend/src/main/jpackage/windows/main.wxs` (nouveau) : le modèle d'installateur de jpackage
  (JDK 21), recopié avec deux ajouts marqués « Libero Shop » :
  - une valeur dans la clé de registre `Run` de l'utilisateur
    (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`), qui pointe sur le lanceur installé ;
  - une action de fin d'installation qui démarre le lanceur, sauf lors d'une désinstallation.
- `backend/src/main/jpackage/windows.args` : `--resource-dir src/main/jpackage/windows`, pour
  que jpackage prenne ce modèle à la place du sien.

Aucun changement dans le code de l'application : tout est dans l'installateur. La clé est celle de
l'utilisateur, pas de la machine, comme l'installation elle-même (`--win-per-user-install`) :
aucun droit d'administrateur n'est demandé.

## Vérifications faites

Avec un installateur d'essai construit par jpackage à partir de ce modèle et des mêmes options
Windows (une application factice, pas Libero Shop) :

- le modèle est accepté par WiX 3.14 ; les seuls avertissements sont ceux du modèle d'origine ;
- installation : l'application a été lancée toute seule, depuis son dossier d'installation ;
- la clé `Run` contient bien le chemin du lanceur, entre guillemets ;
- désinstallation : l'application n'est pas relancée, la clé `Run` et le dossier sont retirés.

## Non vérifié

- **L'installateur de Libero Shop lui-même n'a pas été reconstruit ni installé.** Le PC de
  développement est signalé infecté par un virus (`Expiro`) : un installateur construit dessus
  n'est pas à distribuer tant qu'il n'est pas nettoyé.
- Le démarrage à l'ouverture de session n'a pas été essayé en fermant et rouvrant une session :
  seule la présence de la clé `Run` a été contrôlée.
- La mise à jour par-dessus une version déjà installée (menu « Mettre à jour… ») n'a pas été
  essayée.

## Pour essayer

Sur un poste sain : lancer « Installer Windows (exe) », installer le fichier produit dans
`backend/target/jpackage/dist`. L'icône doit apparaître à la fin de l'installation, puis à
nouveau après une fermeture et réouverture de session.

## Reste ouvert

- **Le modèle est une copie de celui du JDK 21.** Si le JDK qui construit l'installateur change,
  comparer avec le nouveau modèle et y reporter les deux ajouts (la marche à suivre est en tête
  du fichier).
- **Installer par-dessus une version en marche** : l'installateur ne ferme pas l'application.
  Passer par « Mettre à jour… », qui l'arrête d'abord ; sinon deux exemplaires peuvent se
  disputer le même port, et le second affiche une icône rouge.
- **Pas de case à cocher dans le menu de l'icône** pour le démarrage avec Windows : le réglage se
  fait dans Windows. À ajouter si les commerçants le demandent.
