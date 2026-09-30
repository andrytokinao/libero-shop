# Application mobile Android — génération et publication de l'APK

L'application mobile est **la même application Angular**, emballée dans une coque Android par
[Capacitor](https://capacitorjs.com). Ce document explique comment la construire, la signer, la
publier sur le serveur et l'installer sur les téléphones.

---

## 1. Principe

| Élément | Où il est défini | Quand il change |
|---|---|---|
| Écrans (Angular) | `ui/src/` | à chaque version |
| Coque Android | `ui/android/` (généré une fois par Capacitor) | rarement |
| Identité de l'app | `ui/capacitor.config.ts` | jamais (l'`appId` est définitif) |
| **Adresse du serveur** | **sur le téléphone**, saisie au premier lancement | quand le serveur change d'adresse |

**Une seule APK pour toutes les boutiques.** L'adresse du serveur n'est pas dans le build : au
premier lancement, l'application demande le serveur (scan du QR code affiché par
l'administrateur, ou saisie), le vérifie (`GET /api/server/info`), puis l'enregistre sur le
téléphone. Elle se modifie ensuite depuis le menu › *Serveur*, ou depuis le lien sous le
formulaire de connexion.

Le code qui gère cela :

- `ui/src/app/core/config/server-config.service.ts` — l'adresse, sa vérification, son stockage ;
- `ui/src/app/core/interceptors/server-url.interceptor.ts` — envoie les appels `/api` au serveur choisi ;
- `ui/src/app/features/settings/server-setup.component.ts` — l'écran *Serveur* ;
- `ui/src/environments/environment.mobile.ts` — ce qui distingue le build mobile du build web ;
- côté serveur, `backend/.../server/` — l'identité du serveur, ses adresses, le dossier de téléchargement.

---

## 2. Prérequis (poste de build)

- **Node 22** et npm (celui de `ui/node` installé par Maven convient).
- **JDK 21** — le même que pour le backend (`C:\Users\HP\.jdks\ms-21.0.12.1`).
- **Android Studio** récent, avec le SDK Android demandé par Capacitor 8
  (voir <https://capacitorjs.com/docs/getting-started/environment-setup>). Android Studio
  installe le SDK et Gradle au premier lancement.
- Variable `ANDROID_HOME` (ou `ANDROID_SDK_ROOT`) pointant sur le SDK, par exemple
  `C:\Users\HP\AppData\Local\Android\Sdk`.
- **Au moins 10 Go libres** sur le disque : SDK, Gradle et caches.

Ce poste n'a pas besoin d'être le serveur de la boutique. Le build Maven du jar **n'a pas besoin**
d'Android : l'APK se construit à part (voir § 5).

---

## 3. Mise en place — une seule fois

```bash
cd ui
npm install                # installe Capacitor, Preferences, le scanner ML Kit, qrcode
npm run build:mobile       # build Angular mobile → dist/ui-mobile/browser
npx cap add android        # génère le projet natif ui/android/
```

Le dossier `ui/android/` est **à committer** : c'est le projet natif, on y fait les réglages
ci-dessous et Capacitor ne le régénère pas.

### 3.1 Scanner de QR code (ML Kit)

Le scan utilise le scanner Google (Play Services) : **aucune permission caméra** n'est demandée
par l'application. Dans `ui/android/app/src/main/AndroidManifest.xml`, à l'intérieur de
`<application>` :

```xml
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="barcode_ui" />
```

Vérifiez aussi la section *Android* du README de `@capacitor-mlkit/barcode-scanning` (variables
Gradle éventuelles à ajouter dans `ui/android/variables.gradle`).

### 3.2 HTTP sur le réseau local

Le serveur de la boutique est en `http://` sur le Wi-Fi. `capacitor.config.ts` le permet déjà
(`androidScheme: 'http'`, `cleartext: true`) : l'app est servie depuis `http://localhost` et peut
appeler le serveur, REST et WebSocket. Côté serveur, `http://localhost` figure dans
`liberoshop.security.mobile-origins` (`application.properties`).

> Le jour où le serveur a un certificat HTTPS : passer `androidScheme` à `'https'`, retirer
> `cleartext`, et garder `https://localhost` dans `mobile-origins`.

### 3.3 Clé de signature

Android n'installe que des APK signées, et **n'accepte une mise à jour que si elle est signée
avec la même clé**. Cette clé se crée une fois et se conserve précieusement.

```bash
keytool -genkeypair -v ^
  -keystore C:\cles\libero-shop-release.jks ^
  -alias libero-shop -keyalg RSA -keysize 4096 -validity 10000
```

> ⚠️ **Perdre la clé = ne plus pouvoir mettre à jour l'application** sur les téléphones déjà
> équipés (il faudrait la désinstaller partout). Sauvegardez le `.jks` et ses mots de passe
> hors de la machine. Ne le mettez **jamais** dans le dépôt (`*.jks` est dans `.gitignore`).

Créez `ui/android/keystore.properties` (ignoré par git) :

```properties
storeFile=C:/cles/libero-shop-release.jks
storePassword=********
keyAlias=libero-shop
keyPassword=********
```

Puis dans `ui/android/app/build.gradle` :

```groovy
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    // ... ce que Capacitor a généré ...

    signingConfigs {
        release {
            if (keystorePropertiesFile.exists()) {
                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
        }
    }
}
```

---

## 4. Construire une version

1. **Numéro de version** — dans `ui/android/app/build.gradle`, `defaultConfig` :
   - `versionCode` : entier, **à incrémenter à chaque APK publiée** (Android refuse une mise à
     jour dont le `versionCode` n'est pas supérieur) ;
   - `versionName` : affiché à l'utilisateur, par exemple `"1.3.0"`.

2. **Build Angular + copie dans le projet natif** :

   ```bash
   cd ui
   npm run mobile:sync          # = ng build --configuration mobile && npx cap sync android
   ```

3. **APK signée** :

   ```bash
   cd android
   gradlew.bat assembleRelease  # (./gradlew sous Linux/macOS)
   ```

   Résultat : `ui/android/app/build/outputs/apk/release/app-release.apk`.

   Alternative graphique : `npm run mobile:open` ouvre Android Studio, puis
   *Build › Generate Signed App Bundle / APK › APK*.

4. **Vérifier la signature** (facultatif) :

   ```bash
   %ANDROID_HOME%\build-tools\<version>\apksigner verify --print-certs app-release.apk
   ```

---

## 5. Publier sur le serveur

Copiez l'APK dans le dossier de téléchargement du serveur, **sous le nom `libero-shop.apk`** :

```
<dossier d'où le serveur est lancé>\downloads\libero-shop.apk
```

- En développement (`spring-boot:run` depuis `backend/`) : `backend\downloads\libero-shop.apk`.
- Le dossier et le nom se règlent dans `application.properties` :
  `liberoshop.mobile.downloads-dir` et `liberoshop.mobile.apk-file`.
- **Pas de redémarrage** : le fichier est servi tel quel à l'adresse `/downloads/libero-shop.apk`.
- L'APK n'est **pas** dans le jar : publier une nouvelle version de l'app ne demande pas de
  reconstruire le serveur, et le jar ne grossit pas.

**Pourquoi l'APK ne se construit pas avec le jar ?** Il faudrait le SDK Android et la clé de
signature sur chaque machine qui construit le serveur. La clé est un secret, et le build
prendrait plusieurs minutes de plus. On la construit donc à part, à la main ou en CI, puis on la
dépose dans `downloads/`.

---

## 6. Équiper un téléphone

Page **Application mobile** : `http://<serveur>/application-mobile`. Elle est **publique** —
lien sous le formulaire de connexion, et bouton dans la barre latérale pour tous les rôles —
car un nouvel employé équipe son téléphone avant d'avoir un compte.

1. **Où sera le téléphone ?**
   - *Dans la boutique (Wi-Fi)* : adresse du serveur sur le réseau local. Le lien « Changer
     d'adresse » liste toutes les cartes réseau de la machine (adaptateurs virtuels en bas).
     Ces adresses ne sont montrées qu'aux appareils eux-mêmes sur un réseau local.
   - *Partout (Internet)* : adresse publique du serveur, à configurer (§ 6.1). Sans elle, ce
     choix indique que le serveur n'est pas accessible depuis Internet.
2. **QR code 1 — Installer** : le scanner avec l'appareil photo du téléphone (ou toucher
   *Télécharger l'application* si la page est ouverte sur le téléphone), ouvrir le fichier,
   autoriser l'installation depuis le navigateur quand Android le demande.
3. **QR code 2 — Connecter** : ouvrir l'application ; au premier lancement elle affiche l'écran
   *Serveur* → *Scanner le QR code*. L'adresse est vérifiée puis enregistrée, et la page de
   connexion apparaît.

### 6.1 Accès par Internet

Le serveur ne peut pas deviner son adresse publique. Une fois qu'il est joignable depuis
Internet (redirection de port sur la box vers cette machine, ou reverse proxy — de préférence
en HTTPS), renseignez-la :

```properties
liberoshop.mobile.public-url=https://boutique.example.com
```

ou la variable d'environnement `LIBEROSHOP_PUBLIC_URL`. Redémarrage nécessaire.

---

## 7. Quand faut-il une nouvelle APK ?

| Changement | Nouvelle APK ? |
|---|---|
| Backend seul (Java) | Non |
| Écrans Angular (caisse, dépôt, versements…) | **Non** : mise à jour automatique (§ 7.1), à partir de l'APK `versionCode 2` |
| Plugin Capacitor, permission, icône, nom | Oui, et monter `minNativeBuild` (§ 7.2) |
| Adresse du serveur | Non — se change dans l'app (*Serveur*) |

### 7.1 Mise à jour automatique et silencieuse des écrans

Chaque jar contient les écrans mobiles (le build Maven lance aussi `npm run build:mobile`).
Déployer le jar **suffit** à mettre à jour les téléphones :

1. Au lancement et à chaque retour au premier plan, l'app demande au serveur
   `GET /api/mobile/update` quelle version il porte (version = empreinte SHA-256 du zip).
2. Si elle diffère, elle télécharge le zip en arrière-plan (`/api/mobile/bundle/<version>.zip`)
   et vérifie son empreinte.
3. La nouvelle version prend la place **la prochaine fois que l'app passe en arrière-plan** :
   jamais au milieu d'une vente. À la réouverture, c'est la nouvelle version.
4. Si la nouvelle version ne démarre pas (15 s), le téléphone **revient tout seul** à la
   précédente et ne retente plus cette version.

Aucun message à l'utilisateur, aucune donnée envoyée ailleurs qu'au serveur de la boutique
(plugin `@capgo/capacitor-updater`, cloud Capgo et statistiques désactivés dans
`capacitor.config.ts`). Le code : `ui/src/app/core/config/live-update.service.ts`,
`backend/.../server/MobileBundle.java`.

### 7.2 Quand les écrans ont besoin d'une nouvelle APK

`ui/src/mobile/mobile-native.json` indique le `versionCode` minimum de l'APK pour ces écrans.
Quand une version ajoute un plugin ou touche `android/` :

1. monter `versionCode` dans `ui/android/app/build.gradle` ;
2. mettre la même valeur dans `mobile-native.json` ;
3. publier la nouvelle APK (§ 5) **et** déployer le jar.

Les téléphones à l'ancienne APK gardent leurs écrans actuels (rien ne casse) et affichent un
bandeau « Nouvelle version de l'application disponible », qui ouvre la page de téléchargement.

> La **première** APK avec la mise à jour automatique (`versionCode 2`) doit être installée à la
> main sur chaque téléphone : les APK `versionCode 1` ne savent pas se mettre à jour.

---

## 8. Dépannage

| Symptôme | Cause probable / remède |
|---|---|
| « Aucune réponse de http://… » à l'écran *Serveur* | Téléphone sur un autre Wi-Fi ; ou **pare-feu Windows** du serveur. Ouvrir le port : `netsh advfirewall firewall add rule name="Libero Shop" dir=in action=allow protocol=TCP localport=8080` |
| « répond, mais ce n'est pas un serveur Libero Shop » | Mauvaise adresse : c'est la box, une imprimante… Vérifier sur *Admin › Application mobile*. |
| Connexion refusée (403) depuis l'app | L'origine de l'app manque dans `liberoshop.security.mobile-origins` (doit contenir `http://localhost`). |
| Notifications temps réel absentes (pastille grise) | Même cause que ci-dessus : le WebSocket utilise les mêmes origines. |
| « module de scan Google en cours d'installation » | Premier scan : Play Services télécharge le scanner. Réessayer après quelques secondes (réseau Internet nécessaire la première fois). |
| L'adresse du serveur change souvent | L'IP est attribuée par la box (DHCP). Réserver une IP fixe au serveur dans la box. |
| « App non installée » lors d'une mise à jour | APK signée avec une autre clé, ou `versionCode` non incrémenté. |

### Tester sans téléphone

Émulateur Android (Android Studio › Device Manager) : dans l'app, le serveur de la machine hôte
s'appelle `10.0.2.2` → saisir `10.0.2.2:8080`.
