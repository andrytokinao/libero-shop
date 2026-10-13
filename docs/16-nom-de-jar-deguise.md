# Libero Shop — cacher que l'application est écrite en Java (nom du jar déguisé)

Réalisée le 13 octobre 2026.

Demande directe, hors `TODO.md` : faire en sorte que l'application installée ne laisse pas voir
que c'est une application Java, par exemple en renommant le `.jar` (« `app.dll` »), le lanceur
continuant de l'exécuter comme un jar.

---

## Ce qui change

**Avant.** L'installateur jpackage embarquait le fat jar obfusqué sous son nom de build,
`liberoshop-1.0.0.jar`, bien visible dans le dossier `app\` de l'application installée.

**Après.** Le même jar est déposé sous le nom neutre **`libero-shop.dll`**. jpackage le copie tel
quel et écrit `app.classpath=$APPDIR\libero-shop.dll` dans le fichier `.cfg` du lanceur ; la JVM
ouvre l'archive **par son contenu, pas par son extension**, donc le lanceur l'exécute sans rien
changer d'autre. Pour l'utilisateur : un `Libero Shop.exe` natif et un `libero-shop.dll` à côté,
plus aucun fichier en `.jar`.

## Ce que ça cache, et ce que ça ne cache pas

C'est un **camouflage de surface**, assumé comme tel :

- Le dossier `runtime\` reste un runtime Java (`jvm.dll`, `java.exe`, fichier `release` avec
  `JAVA_VERSION`…) : qui regarde ce dossier voit que c'est une JVM.
- Le `libero-shop.dll` reste une archive ZIP : ouvert avec 7-Zip, il montre `BOOT-INF/`,
  `META-INF/`, des `.class`.

La **vraie** protection contre la rétro-ingénierie reste l'obfuscation ProGuard déjà en place
(on la voit à l'exécution : les classes applicatives apparaissent sous des noms comme
`com.houssen.liberoshop.a.o`). L'extension ne protège rien ; elle masque seulement le nom.

Le nom et l'extension se changent d'un seul endroit : la propriété `jpackage.mainjar` du profil
`installer`. Une extension plus neutre (`.bin`, `.pak`, `.dat`) serait moins surprenante qu'un
`.dll` qui n'est pas un vrai binaire PE ; au besoin, il suffit d'y changer la valeur.

## Détail

- `backend/pom.xml` (profil `installer`) :
  - nouvelle propriété `jpackage.mainjar` = `libero-shop.dll` ;
  - étape `maven-antrun-plugin` (`disguise-jpackage-mainjar`, phase `verify`) qui renomme le jar
    staged juste après sa copie et avant jpackage — l'ordre d'exécution dans la phase `verify` est
    donc : stage du jar → renommage → jpackage ;
  - l'argument `--main-jar` de jpackage pointe désormais sur `${jpackage.mainjar}` au lieu de
    `${project.build.finalName}.jar`.

Aucun autre fichier ne référençait le nom du jar en dur : ni le script Inno Setup
(`innosetup/liberty-shop.iss`, qui embarque tout le dossier avec `*`), ni le modèle WiX
(`jpackage/windows/main.wxs`). Le redémarrage en cours de process (`ApplicationRestarter`) relance
le contexte Spring **dans la même JVM**, sans `java -jar`, donc le nom du jar n'y intervient pas.
Le garde-fou de licence (`LicensePropertyGuard.isDelivered()`) compare l'emplacement du code au
chemin de développement `/target/classes/`, pas à une extension : un jar nommé `.dll` est traité
comme « livré » exactement comme avant.

## Vérifications faites

- **App-image portable reconstruite** avec un JDK 21 :
  `mvnw -Pinstaller -Dinstaller.os=portable -DskipTests verify` → succès. Le dossier `app\` ne
  contient **aucun `.jar`** : `libero-shop.dll` (87 Mo) et `libero-shop.apk`. Le `.cfg` généré
  porte bien `app.classpath=$APPDIR\libero-shop.dll`.
- **Démarrage réel depuis l'archive renommée** : `java -jar libero-shop.dll` (tray désactivé, base
  H2 en mémoire, licence désactivée) boote complètement via `JarLauncher` jusqu'à
  « Started BackendApplication », Tomcat à l'écoute. L'extension `.dll` n'empêche rien.
- Test isolé préalable : une application d'essai construite par jpackage avec un `--main-jar` en
  `.dll` se lance (exit 0), ce qui a confirmé que jpackage accepte une extension non-`.jar`.

## Non vérifié

- **L'installateur Windows `.exe` (WiX) n'a pas été reconstruit** : WiX Toolset n'est pas présent
  sur ce poste. Le chemin `-Dinstaller.os=windows` utilise le même `--main-jar`, donc le
  comportement est identique à celui de l'app-image portable vérifiée, mais l'`.exe` final et son
  installation n'ont pas été essayés ici.
- Le `Setup.exe` Inno Setup n'a pas été recompilé (il reprend le dossier tel quel, sans
  dépendre du nom du jar).

## Pour essayer

Sur un poste avec WiX : `mvnw -Pinstaller -Dinstaller.os=windows verify`, installer, lancer. Le
dossier d'installation ne doit montrer aucun `.jar`, et l'application démarre normalement.

## Reste ouvert

- Si une extension `.dll` pose problème (antivirus qui inspecte, outil qui tente de la charger
  comme bibliothèque), basculer `jpackage.mainjar` sur `.bin`/`.pak` ne coûte qu'un mot.
