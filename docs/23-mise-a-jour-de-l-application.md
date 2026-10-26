# Moussa Shop — mise à jour de l'application sans réinstaller

Réalisée le 15 octobre 2026.

Demande directe, hors `TODO.md` : pouvoir installer une nouvelle version **sans réinstaller le
serveur** — télécharger et remplacer l'existant. Les trois moyens demandés, disponibles en même
temps : **automatique** (piloté par une URL et des réglages), **par un bouton** (vérifier et
télécharger), et par **envoi manuel** d'un fichier sur une page présente mais **cachée** (aucune
entrée de menu n'y mène).

---

## Ce qui change

**Avant.** Une nouvelle version n'arrivait que par l'installateur Windows complet : le menu de
l'icône « Mettre à jour… » demandait de choisir un fichier `.exe`/`.msi`, arrêtait le serveur, le
posait par-dessus, puis le relançait. Lourd, et seulement depuis l'ordinateur du serveur.

**Après.** Une nouvelle version arrive comme un seul fichier léger, un `.lsu`, qui ne contient que
le programme (pas la machine Java, déjà installée). L'administrateur l'installe sans réinstaller
quoi que ce soit, de **trois** façons :

1. **Page cachée, envoi manuel.** Une page réservée au super-admin, `/admin/mise-à-jour`, **sans
   entrée de menu** (comme la page Licence) : on y dépose le fichier `.lsu`, le serveur le
   vérifie, remplace son propre programme et redémarre sur la nouvelle version.
2. **Bouton « Vérifier maintenant ».** Quand une adresse de mise à jour est configurée, la page
   interroge cette adresse, dit si une version plus récente existe, et propose de la télécharger
   et de l'installer.
3. **Automatique.** Si on l'active (et qu'une adresse est donnée), le serveur vérifie tout seul
   une fois par jour, à une **heure configurable**. Quand il trouve une version, il ne coupe pas
   brutalement : il **demande d'abord à tous les écrans connectés** s'ils peuvent être coupés, et
   n'installe qu'une fois que **tous ont accepté** (réglable).

Dans tous les cas, le serveur **garde la version précédente** : un bouton « Revenir à la version
précédente » la remet en place si la nouvelle pose problème.

La sécurité ne dépend d'aucun de ces chemins : le serveur **n'exécute que du code signé par
l'éditeur**. Un fichier modifié, incomplet, ou qui ne vient pas de l'éditeur, est refusé — qu'il
ait été déposé à la main, téléchargé par le bouton, ou récupéré automatiquement.

## Les réglages (fichier `application.properties`, section `liberoshop.update`)

- `enabled` — interrupteur de la vérification **automatique**. `false` par défaut : une
  installation ne contacte jamais l'extérieur d'elle-même.
- `url` — l'adresse où lire le manifeste de mise à jour. **Vide** : plus aucune opération en ligne
  (bouton et automatique), seul l'envoi manuel reste. Coupe donc l'automatique même si `enabled`.
- `check-time` — l'heure de la vérification quotidienne (défaut `03:00`, hors heures d'ouverture).
- `require-confirmation` — attendre l'accord de chaque écran connecté avant de couper (défaut
  `true`).
- `confirmation-timeout` — combien de temps attendre ces accords avant de reporter (défaut `5m`).

L'`url`, l'`enabled` et l'heure se laissent aussi piloter par variables d'environnement
(`LIBEROSHOP_UPDATE_URL`, `LIBEROSHOP_UPDATE_ENABLED`, `LIBEROSHOP_UPDATE_CHECK_TIME`).

## Comment c'est fait (détail technique)

### Le fichier `.lsu` et sa vérification

Un `.lsu` est une archive ZIP de deux pièces : `envelope.json` (petit : format, version,
algorithme, le **manifeste signé** sous forme de blob opaque, et sa signature détachée) et
`payload.bin` (le gros fichier `.jar` de l'application). Le manifeste signé contient la version,
la date, une note, et l'**empreinte SHA-256** du payload. La signature couvre le manifeste ; le
manifeste épingle le payload par son empreinte — donc la signature couvre aussi le programme.

- `EmbeddedUpdateKey` : clé **publique Ed25519** compilée dans l'application, **distincte** de
  celle de la licence (signer une mise à jour, c'est livrer du code qui s'exécute ; signer une
  licence ne fait que déverrouiller). Seule la clé **privée** correspondante, détenue par
  l'éditeur (jamais dans le dépôt, voir `backend/update-signing/`), produit une signature acceptée.
- `UpdatePackage` lit le ZIP ; `UpdateVerifier` vérifie, **dans l'ordre** : algorithme, signature
  sur les octets du manifeste, puis que l'empreinte du payload correspond, puis (avant d'appliquer)
  que la version est **strictement plus récente** que celle installée. Même logique et même
  vigilance que le module licence.

### Le remplacement du programme et le redémarrage (Windows)

Le programme en marche ne peut pas se remplacer lui-même : c'est le `.jar` dont la JVM a chargé ses
classes, et Windows le verrouille tant que le processus tourne. Le remplacement est donc confié à
un petit **script externe** qui survit au processus :

- `InstallLayout` retrouve, depuis la JVM, l'emplacement installé : le payload (unique entrée du
  classpath — sur l'installation Windows, `runtime\lib\resources.bin`) et le lanceur
  (`Moussa Shop.exe`, déduit du `.cfg`). Hors installation livrée (développement) ou hors Windows,
  rien n'est trouvé et la mise à jour en place est **refusée proprement**.
- `UpdateInstaller` dépose le nouveau `.jar` à côté (`.new`), écrit un script `.cmd`, puis ferme le
  contexte (la base H2 avec) et quitte. Le script **attend que le verrou du fichier se libère**
  (il réessaie simplement de renommer le payload — pas de `tasklist` dont le texte est traduit),
  échange les fichiers en gardant l'ancien en `.bak`, et relance le lanceur. La nouvelle version
  démarre dans une JVM neuve.
- Retour arrière : `rollback()` remet le `.bak` en place par le même mécanisme.

### En ligne et automatique

- `UpdateSource` (client HTTP du JDK) lit le manifeste à l'`url` configurée et télécharge le
  `.lsu`. Le manifeste n'est qu'une **indication** (où chercher) : le fichier téléchargé est
  vérifié exactement comme un envoi manuel.
- `UpdateScheduler` : son propre petit ordonnanceur (pas de `@Scheduled` global), qui se tait quand
  le canal est éteint, vérifie à l'heure dite, et reprogramme le lendemain. Une vérification qui
  échoue (serveur injoignable) est consignée et avalée.
- `UpdateCoordinator` : quand une mise à jour est prête, il diffuse une demande de confirmation sur
  le canal temps réel `maintenance` (le même WebSocket STOMP que les autres notifications), attend
  l'accord de tous les écrans connectés, et n'applique qu'alors ; un refus ou le dépassement du
  délai reporte. Les endpoints `/api/update/confirm` et `/decline` sont ouverts à tout compte
  connecté (chacun répond pour son propre poste ; l'identité vient du jeton, pas du corps de la
  requête).

### Côté écran (Angular)

- Page cachée `/admin/mise-à-jour` (`UpdateComponent`), déclarée dans les routes mais **absente de
  `navigation.ts`** : joignable seulement en tapant l'adresse. Statut, envoi de fichier,
  vérification en ligne, retour arrière, et suivi du redémarrage (comme la restauration de
  sauvegarde).
- `MaintenanceWatcherComponent`, posé dans le shell (donc sur **tous** les écrans) : affiche la
  modale « mise à jour disponible, ce poste peut-il être coupé ? » et, au moment de la coupure,
  l'écran « le serveur revient » avant de recharger.

### Signer une version (éditeur)

Outil `tools/update-signer/UpdateSigner.java`, sans dépendances, lancé depuis la source :

```
# produire une paire de clés (colle la publique dans EmbeddedUpdateKey, garde la privée secrète)
java UpdateSigner.java keygen update-private.key

# signer un .jar en .lsu
java UpdateSigner.java sign --key backend/update-signing/update-private.key \
     --jar backend/target/liberoshop-0.0.1-SNAPSHOT.jar \
     --version 1.3.0 --notes "Corrections diverses." --out libero-shop-1.3.0.lsu
```

## Choix faits (signalés, non bloquants)

- **Clé de mise à jour neuve.** L'ancienne clé/outil de signature des licences
  (`tools/license-generator`) a été retiré du dépôt ; impossible de la réutiliser. Une paire
  Ed25519 **dédiée aux mises à jour** a donc été générée, sa clé publique embarquée, et sa clé
  privée enregistrée hors dépôt (`backend/update-signing/`, ignorée par git). À remplacer par
  l'éditeur s'il veut sa propre clé (`keygen`, coller, reconstruire).
- **Portée : Windows.** Le déguisement du payload et la relance par `.cmd` visent l'installation
  Windows (la cible). Sur une autre plateforme ou en développement, la mise à jour en place se
  refuse proprement et la page le dit.
- **Confirmation = courtoisie.** Le serveur n'a pas d'état « vente en cours » (une vente est une
  seule transaction au moment de l'encaissement) : la confirmation protège l'opérateur d'une
  coupure surprise, elle n'évite pas une perte de données, qu'il n'y a pas.

## Vérifications faites

- **Signature et paquet** (`UpdateVerifierTest`, `VersionsTest`) : un paquet authentique est
  accepté ; signé par une autre clé, signature cassée, payload modifié après signature, algorithme
  non supporté, version non plus récente, fichier qui n'est pas une archive — tous refusés.
- **Emplacement et échange** (`InstallLayoutTest`, `UpdateInstallerTest`) : hors installation
  livrée, `canApply()` est faux ; le staging écrit bien le nouveau payload et le script `.cmd`
  contient les quatre chemins substitués et les bonnes commandes.
- **Endpoints** (`UpdateControllerTest`) : le statut n'est servi qu'au super-admin (403 pour un
  autre rôle, 401 sans jeton), et dit « mise à jour en place indisponible » sous les tests.
- **Confirmation** (`UpdateCoordinatorTest`) : application immédiate quand personne n'est connecté
  ou quand la confirmation est désactivée ; attente de tous les écrans sinon ; un refus ou le délai
  dépassé reporte.
- **Manifeste distant** (`RemoteUpdateInfoTest`, `UpdateSourceTest`) : lecture d'un manifeste
  complet, refus s'il manque la version ou l'URL, refus d'une URL non http(s), refus de toute
  opération en ligne sans URL configurée.
- **Chaîne complète signeur → clé embarquée** : un `.lsu` produit par `UpdateSigner` avec la clé
  privée enregistrée est **accepté par la clé publique embarquée**, empreinte du payload concordante
  (essai manuel, confirmé).
- Interface : `tsc --noEmit` passe ; le contexte Spring complet démarre avec tous les nouveaux
  beans.

## Non vérifié

- **Le remplacement réel et le redémarrage n'ont pas été essayés sur une installation Windows.**
  L'installateur n'a pas pu être reconstruit ni installé ici (le PC de développement est signalé
  infecté, cf. `14-lancement-automatique-apres-installation.md`), donc : le verrou du `.jar` en
  marche, l'échange par le `.cmd`, la relance du lanceur et le retour arrière reposent sur le
  raisonnement et sur des tests unitaires, pas sur un essai bout-en-bout en conditions réelles.
- **La modale de confirmation et l'écran de redémarrage n'ont pas été vus dans un navigateur** :
  l'abonnement au canal `maintenance`, les boutons Oui/Pas maintenant et le rechargement reposent
  sur le code existant (temps réel, suivi de redémarrage de la restauration), non rejoués à la main.
- **`ng test` et `ng build` n'ont pas été relancés** (seul `tsc --noEmit` l'a été).
- **Aucun vrai serveur de mise à jour en ligne** n'a été monté : le téléchargement et l'installation
  en ligne n'ont pas été essayés contre un vrai manifeste HTTP.

## Pour essayer

Sur un poste sain, après avoir reconstruit et installé l'application, puis signé un `.lsu` d'une
version supérieure :

1. Ouvrir `/admin/mise-à-jour` en super-admin, déposer le `.lsu`, « Installer et redémarrer » : le
   serveur doit revenir sur la nouvelle version, l'ancienne restant disponible en retour arrière.
2. Pour l'automatique : poser `liberoshop.update.url` sur un manifeste réel, `enabled=true`, une
   `check-time` proche ; ouvrir une caisse sur un autre poste ; à l'heure dite, la caisse doit voir
   la demande de confirmation, et l'installation attendre son « Oui ».

## Reste ouvert

- **Détection d'un démarrage raté après mise à jour.** Le retour arrière est manuel : si la
  nouvelle version ne démarre pas, l'`.bak` est là mais rien ne la remet automatiquement. Un marqueur
  « premier démarrage » effacé seulement après un boot réussi permettrait un retour arrière
  automatique.
- **Icône fantôme possible.** La sortie se fait par `halt` (après fermeture du contexte) sans passer
  par le `quit()` de l'icône : Windows peut laisser une icône morte dans la zone de notification
  jusqu'au passage de la souris. À router par l'icône si gênant.
- **Un écran qui se connecte pendant une demande de confirmation** n'est pas réinterrogé ; il est
  ignoré jusqu'à l'application. Rare, mais à garder en tête.
- **Portée non-Windows et chemins non-ASCII.** La relance `.cmd` n'est pas écrite pour macOS/Linux,
  ni testée pour un dossier d'installation au nom non-ASCII.
