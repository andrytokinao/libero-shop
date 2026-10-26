# Moussa Shop — application mobile livrée avec l'installateur

Réalisée le 11 octobre 2026.

Demande directe, hors `TODO.md` : sur une application installée, on ne trouvait pas le lien de
téléchargement de l'application mobile.

---

## Ce qui change

**Avant.** La page « Application mobile » ne propose le téléchargement que si le fichier
`libero-shop.apk` a été copié à la main dans le dossier `downloads` du serveur. L'installateur
Windows ne le contenait pas : sur un poste fraîchement installé, la page affichait
« L'application n'est pas encore disponible sur ce serveur », sans QR code ni bouton.

**Après.**

1. **L'installateur contient l'application mobile.** Dès la première ouverture, la page
   « Application mobile » montre le QR code et le bouton « Télécharger l'application », sans rien
   copier.
2. **Copier une APK dans le dossier `downloads` reste possible**, pour publier une nouvelle
   version sans refaire l'installateur. Entre les deux fichiers, les téléphones reçoivent **le plus
   récent** : une vieille copie oubliée dans le dossier ne masque pas celle d'un nouvel
   installateur, et inversement.
3. Rien d'autre du dossier de l'application n'est téléchargeable : seul ce fichier, sous ce nom.

## À savoir pour construire l'installateur

L'installateur emporte l'APK qui se trouve dans `backend\downloads\libero-shop.apk` **au moment
où il est construit** — là où la configuration « APK Android (release) » la dépose. Donc :

1. construire l'APK (`ui\build-apk.cmd`) ;
2. puis construire l'installateur.

Si le fichier est absent, l'installateur se construit quand même, sans application mobile, et la
page le dit à l'administrateur.

## Détail

- `backend/pom.xml` (profil `installer`) : `downloads/libero-shop.apk` est copié à côté du jar
  dans le dossier que jpackage embarque.
- `backend/src/main/jpackage/runtime.args` : `-Dliberoshop.mobile.bundled-apk=$APPDIR/libero-shop.apk`
  (`$APPDIR` est remplacé par le lanceur, au démarrage, par le dossier d'installation).
- `server/MobileProperties.java` : nouveau réglage `bundled-apk` et `publishedApk()`, qui choisit
  le plus récent des deux fichiers.
- `server/DownloadsConfiguration.java`, `server/ServerController.java` : servent et annoncent ce
  fichier-là.
- `ui/.../mobile-download.component.ts` : le message destiné à l'administrateur quand il n'y a
  aucune APK.

Fichiers : `backend/pom.xml`, `jpackage/runtime.args`, `resources/application.properties`,
`server/MobileProperties.java`, `server/DownloadsConfiguration.java`,
`server/ServerController.java`, `server/ServerConnectionResponse.java` ; tests
`server/MobilePropertiesTest.java` et `server/BundledApkDownloadTest.java` (nouveaux) ;
`ui/src/app/features/mobile/mobile-download.component.ts` ; `docs/mobile-android.md` (§ 5).

## Vérifications faites

- **Tests automatiques du serveur : 544, tous verts**, dont 6 nouveaux :
  - sans aucune APK : rien à télécharger ; installation neuve : celle de l'installateur ; la plus
    récente des deux gagne, dans les deux sens (`MobilePropertiesTest`) ;
  - dossier `downloads` vide : `/downloads/libero-shop.apk` renvoie l'APK de l'installateur, la
    page l'annonce, et le jar posé à côté n'est pas téléchargeable (`BundledApkDownloadTest`).
- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe.
- `$APPDIR` : vérifié avec une application d'essai construite par jpackage — le réglage arrive
  bien avec le chemin du dossier d'installation, et le fichier y est trouvé.

## Non vérifié

- **L'installateur de Moussa Shop n'a pas été reconstruit ni installé** (PC de développement
  signalé infecté, voir `14-lancement-automatique-apres-installation.md`). Le téléchargement
  depuis un vrai téléphone n'a pas été essayé.

## Pour essayer

Sur un poste sain : construire l'APK, puis l'installateur ; installer ; ouvrir « Télécharger
l'application mobile Android » depuis la page de connexion. Le QR code et le bouton doivent être
là, avec la taille et la date de l'APK.

## Reste ouvert

- L'installateur grossit de la taille de l'APK (environ 27 Mo).
- Le poste de développement contient une seconde copie de l'APK dans `ui/src/app/release/`, qui
  ne sert à rien de ce qui précède ; son fichier `output-metadata.json` est suivi par git. À
  ranger ou supprimer.
