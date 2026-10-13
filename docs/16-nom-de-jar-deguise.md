# Libero Shop — cacher que l'application est écrite en Java (payload déguisé dans le runtime)

Réalisée le 13 octobre 2026.

Demande directe, hors `TODO.md` : faire en sorte que l'application installée ne laisse pas voir
que c'est une application Java — extension neutre, nom de fichier qui ne dit rien, et si possible
glisser le fichier parmi ceux du runtime pour qu'on ne devine pas quelle est « la source » à
attaquer.

---

## Ce qui change

**Avant.** L'installateur jpackage posait le fat jar obfusqué dans `app\`, sous son nom de build
`liberoshop-0.0.1-SNAPSHOT.jar`. Ce `.jar` bien visible annonçait « application Java ».

**Après (build Windows).** Après la construction de l'image, le jar est :

1. **déplacé hors de `app\`**, dans `runtime\lib\` — le dossier des fichiers de la JVM ;
2. **renommé** `resources.bin` : extension neutre, nom qui n'évoque ni Libero Shop ni Java ;
3. le fichier `.cfg` du lanceur est réécrit pour l'y chercher
   (`app.classpath=$APPDIR\..\runtime\lib\resources.bin`).

Résultat dans le dossier installé :

- `app\` ne contient plus qu'un `.cfg` (et l'APK mobile) — **aucun `.jar`, aucun exécutable
  Java apparent** ;
- `resources.bin` (87 Mo) est posé dans `runtime\lib\` **juste à côté de `modules` (91 Mo)**,
  `tzdb.dat`, `jvm.cfg`, `jrt-fs.jar` : deux gros blobs qui se ressemblent, l'application se fond
  parmi les fichiers du moteur Java. La JVM ouvre l'archive par son contenu, pas par son
  extension, donc le lanceur l'exécute sans rien changer.

Le nom et l'extension se règlent d'un seul endroit : la propriété `jpackage.payload` du profil
`installer` (mettre `moteur.dat`, `core.pak`, etc. n'y coûte qu'un mot).

## Ce que ça cache, et ce que ça ne cache pas

C'est un **brouillage de surface**, assumé :

- Le dossier `runtime\` reste un runtime Java (`java.exe`, `jvm.dll`, fichier `release` avec
  `JAVA_VERSION`…). Qui l'ouvre voit une JVM.
- `resources.bin` reste une archive ZIP : ouvert avec 7-Zip, il montre `BOOT-INF/`, `META-INF/`,
  des `.class`. C'est aussi le seul fichier anormalement gros de `runtime\lib\`, donc repérable
  par qui connaît la structure d'un runtime jlink.

La **vraie** protection contre la décompilation reste l'obfuscation ProGuard déjà en place (on la
voit à l'exécution : les classes applicatives apparaissent sous des noms comme
`com.houssen.liberoshop.a.o`). Le déguisement rend seulement la source plus difficile à repérer au
premier coup d'œil ; il ne remplace pas l'obfuscation.

## Détail

- `backend/pom.xml` (profil `installer`) :
  - propriétés `installer.name` (= `Libero Shop`, sert aussi à localiser le dossier de l'image) et
    `jpackage.payload` (= `resources.bin`) ;
  - nouvelle étape `maven-antrun-plugin` (`hide-payload-in-runtime`, phase `verify`), **déclarée
    après** l'exécution jpackage pour passer une fois l'image construite. Elle :
    - ne s'active que **sous Windows** et seulement si un dossier d'image `app\` existe
      (condition Ant `<os family="windows">` + `<available>`) ;
    - `<move>` le jar de `app\` vers `runtime\lib\resources.bin` ;
    - `<replace>` (remplacement littéral, pas regexp — sinon les antislashs du chemin Windows
      seraient mangés) réécrit la ligne `app.classpath` du `.cfg`.

Rien d'autre ne référençait le nom du jar : ni le script Inno Setup (`innosetup/liberty-shop.iss`,
qui embarque tout le dossier avec `*`), ni le modèle WiX (`jpackage/windows/main.wxs`). Le
redémarrage en cours de process (`ApplicationRestarter`) relance le contexte Spring **dans la même
JVM**, sans `java -jar`, donc le nom du jar n'y intervient pas. Le garde-fou de licence
(`LicensePropertyGuard.isDelivered()`) compare l'emplacement du code au chemin de développement
`/target/classes/`, pas à une extension : l'archive renommée est traitée comme « livrée » comme
avant.

## Portée

- **Pris en charge : le build Windows portable** (`-Dinstaller.os=portable`), c'est-à-dire l'image
  que **l'installateur Inno Setup `LiberoShop-Setup-*.exe` emballe** — donc l'installateur Windows
  livré hérite du déguisement.
- **Non pris en charge : le build « tout-en-un » WiX** (`-Dinstaller.os=windows`, `--type exe`) et
  les builds Linux/macOS. Là, jpackage produit directement un fichier d'installation sans laisser
  d'image intermédiaire à retravailler : l'étape se désactive d'elle-même (garde `<available>`) et
  le jar y garde son nom. Pour les couvrir il faudrait un jpackage en deux temps (image, puis
  emballage via `--app-image`) — non fait ici.

## Vérifications faites

- **Image portable reconstruite proprement** avec un JDK 21 :
  `mvnw -Pinstaller -Dinstaller.os=portable -DskipTests clean verify` → succès.
  - `app\` ne contient **aucun `.jar`** : seulement `.jpackage.xml`, `Libero Shop.cfg`,
    `libero-shop.apk` ;
  - `runtime\lib\resources.bin` (87 Mo) présent, à côté de `modules`, `tzdb.dat`, `jvm.cfg` ;
  - `.cfg` : `app.classpath=$APPDIR\..\runtime\lib\resources.bin` (antislashs préservés).
- **Démarrage réel depuis l'archive relocalisée** : lancée comme le fait le lanceur
  (`-cp runtime\lib\resources.bin org.springframework.boot.loader.launch.JarLauncher`, tray
  désactivé, base H2 en mémoire), l'application boote jusqu'à « Started BackendApplication »,
  Tomcat à l'écoute. L'extension `.bin` et l'emplacement dans `runtime\` n'empêchent rien.
- Tests isolés préalables (appli d'essai jpackage) : jpackage accepte une archive sans extension
  `.jar` ; le lanceur suit un classpath `$APPDIR\..\runtime\lib\...`.

## À savoir pour construire

- **Toujours construire avec `clean`** : jpackage refuse d'écrire si le dossier de destination
  existe déjà, et le dossier `input/` de staging n'est pas vidé entre deux builds (un reliquat y
  resterait embarqué dans l'image).

## Non vérifié

- **L'installateur WiX `.exe` n'a pas été reconstruit** (WiX Toolset absent de ce poste). De toute
  façon l'étape ne le concerne pas (voir « Portée »).

## Reste ouvert

- Étendre le déguisement au build WiX et aux autres OS, via un jpackage en deux temps.
- `resources.bin` reste le plus gros fichier de `runtime\lib\` : pour un camouflage plus poussé, on
  pourrait le découper ou mimer un nom de module jlink.
