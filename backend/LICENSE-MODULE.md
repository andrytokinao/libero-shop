# Module de licence — Libero Shop

Protection de l'application installée chez le client, sans SaaS et sans dépendance à
Internet. La seule source de vérité est un fichier signé sur le poste du client ; rien
n'est stocké en base, donc il n'y a aucun booléen `is_active` à basculer avec un client SQL.

---

## 1. Principe

| Élément | Choix | Raison |
|---|---|---|
| Signature | Ed25519 (`java.security`, natif JDK 15+) | Aucune dépendance, signature de 64 octets, pas de piège de padding |
| Clé publique | Compilée en dur dans `EmbeddedLicenseKey` | Si elle était configurable, le client signerait ses propres licences |
| Clé privée | Module séparé `tools/license-generator`, jamais livré | Ne peut pas se retrouver dans le jar client par accident |
| Empreinte machine | Nom de machine + numéro de série du volume système | Survit aux changements réseau (dock USB, Wi-Fi, VPN) qui bloqueraient une empreinte MAC |
| Expiration | Dégradation en lecture seule après 15 jours de grâce | Un supermarché ne doit pas s'arrêter un samedi midi |
| Recul d'horloge | Fichier d'état authentifié par HMAC | Remettre l'horloge en arrière ne rallonge pas la licence |
| Absence de licence | Essai de 90 jours, **une seule fois par machine** | Une installation neuve doit pouvoir démarrer ; supprimer le dossier `license/` ne doit pas redonner 90 jours |
| Mémoire de l'essai | Base de données du client + fichiers + registre | Les fichiers se trouvent en trois minutes avec un moniteur de fichiers ; la base, elle, ne s'efface pas sans effacer les ventes |

### Format du fichier `.lic`

Une enveloppe JSON contenant la charge utile **encodée**, plus sa signature détachée :

```json
{
  "format": "libero-shop-license",
  "version": 1,
  "algorithm": "Ed25519",
  "payload": "eyJsaWNlbnNlSWQiOi...",
  "signature": "9Qm1f..."
}
```

La charge utile décodée :

```json
{
  "licenseId": "CUST-0001-2027-1790216836",
  "customerId": "CUST-0001",
  "customerName": "Supermarche Houssen Analakely",
  "plan": "ANNUAL",
  "issuedOn": "2026-09-24",
  "expiresOn": "2027-09-24",
  "graceDays": 15,
  "machineFingerprints": ["LS1-K5389-06M4Y-SWPET-XQ292"]
}
```

> **Pourquoi la charge utile est encodée plutôt qu'imbriquée en JSON clair.** La signature
> porte sur les octets exacts de la charge utile. Le client n'a donc jamais à re-sérialiser
> du JSON pour vérifier : ordre des clés, espaces, format des nombres et échappement
> Unicode deviennent sans effet. C'est ce qui élimine toute la famille de bugs
> « licence valide refusée sur le poste du client ».

---

## 2. Côté éditeur : `tools/license-generator`

Module Maven autonome, **zéro dépendance**, à ne jamais livrer.

```bash
cd tools/license-generator
mvn package

# 1. Une seule fois. Sauvegardez license-private.key hors ligne (jamais dans git).
java -jar target/license-generator.jar keygen --out-dir ./keys
# -> affiche la clé publique à coller dans EmbeddedLicenseKey.PUBLIC_KEY_BASE64

# 2. Pour chaque client, avec l'empreinte relevée sur son poste
java -jar target/license-generator.jar issue \
     --private-key ./keys/license-private.key \
     --customer-id CUST-0042 \
     --customer-name "Supermarche Houssen Analakely" \
     --fingerprint LS1-4KQ8T-9WZ2M-H7PXR-C3NVB \
     --plan ANNUAL \
     --out ./out/CUST-0042.lic

# 3. Contrôle avant envoi
java -jar target/license-generator.jar inspect \
     --file ./out/CUST-0042.lic --public-key ./keys/license-public.key
```

**Renouvellement — utilisez `renew`, pas `issue`.** Le client vous envoie son *code de
renouvellement* (§6 bis) et la commande en extrait tout ce qui se ressaisissait à la main :

```bash
java -jar target/license-generator.jar renew \
     --private-key ./keys/license-private.key \
     --request LSR1-04JEX-397KW-AJ4YV-Q1GEQ-DC021-C3GK6-RBNQ5-FWBSY-4CS78 \
     --customer-id CUST-0042 --customer-name "Supermarche Houssen Analakely" \
     --plan MONTHLY \
     --out ./out/CUST-0042-renouvellement.lic
```

```
Renewal request
  machine      : LS1-4KQ8T-9WZ2M-H7PXR-C3NVB
  state        : GRACE (expired, still usable)
  current end  : 2027-09-24
  produced on  : 2026-09-25 (0 day(s) ago)
  reference    : 0BADCAFE

License issued: .../CUST-0042-renouvellement.lic
  valid        : 2027-09-24 -> 2027-10-24 (+15 grace days)
```

L'empreinte vient du code — vingt caractères jamais retapés — et `--starts-on` est **déduit
de l'expiration que le code annonce**, donc le client ne perd pas les jours déjà payés sans
que personne consulte un tableur. Un code produit il y a plus de 30 jours est refusé
(`--max-age-days` pour passer outre) : renouveler depuis un code périmé, c'est renouveler
depuis une date d'expiration périmée.

Tout reste surchargeable : un `--starts-on` ou un `--fingerprint` supplémentaire est honoré,
pour un client multi-caisses ou un geste commercial.

`issue` reste la commande d'une **première** licence commandée par téléphone, quand vous
n'avez que l'empreinte.

Options utiles : `--plan MONTHLY|ANNUAL|TRIAL`, `--months <n>`, `--expires-on`,
`--grace-days <n>`, `--fingerprint` répétable (plusieurs caisses), `--notes`.

> **Prolonger une évaluation ne se fait pas en rallongeant l'essai.** Émettez une vraie
> licence courte, `--plan TRIAL --months 1` : elle est signée, tracée, et `install`
> l'accepte même si elle expire avant la fin de l'essai en cours. Un essai qui se renouvelle
> serait exactement le trou que `TrialRegistry` existe pour fermer.

⚠️ **La clé privée est le seul secret du dispositif.** Qui la détient peut générer des
licences illimitées. Sauvegarde chiffrée hors ligne, jamais dans le dépôt
(`tools/license-generator/.gitignore` couvre déjà `keys/`).

---

## 3. Côté client : déroulé au démarrage

1. `LicenseStartupListener` s'exécute sur `ApplicationEnvironmentPreparedEvent` — la
   configuration est chargée, mais **aucun bean n'est créé et aucune connexion base n'est
   ouverte**.
2. Lecture du fichier → vérification de la signature → vérification de la machine →
   évaluation des dates.
3. Issue :
   - **Signature invalide / mauvaise machine** → le démarrage est refusé, avec un encadré
     lisible en console indiquant le chemin attendu et l'empreinte machine.
   - **Fichier absent** → bascule sur la période d'essai (§ 4 bis), encadré en console avec
     l'empreinte à communiquer. Si l'essai a déjà été consommé sur cette machine,
     l'application démarre directement en lecture seule. Avec
     `liberoshop.license.trial.enabled=false`, le démarrage est refusé comme avant.
   - **Expirée** → l'application démarre en avertissant, puis dégrade.
4. `LicenseService` refait la vérification dans le contexte Spring (quelques
   millisecondes), ce qui couvre les chemins qui ne passent pas par `main()`.

Le listener est enregistré explicitement dans `BackendApplication.main` plutôt que via
`spring.factories` : le câblage reste visible en un seul endroit, et surtout `@SpringBootTest`
(qui n'appelle pas `main()`) n'exige pas de vraie licence sur la machine de build.

### Exceptions

| Exception | Code | Moment | Effet |
|---|---|---|---|
| `LicenseNotFoundException` | `LICENSE_NOT_FOUND` | Démarrage | Démarrage refusé — uniquement si l'essai est désactivé |
| `LicenseInvalidException` | `LICENSE_INVALID` | Démarrage | Démarrage refusé |
| `LicenseMachineMismatchException` | `LICENSE_MACHINE_MISMATCH` | Démarrage | Démarrage refusé |
| `LicenseExpiredException` | `LICENSE_EXPIRED` | À l'écriture | HTTP 402, lecture seule |

`LicenseExpiredException` n'est volontairement **pas** levée au démarrage : l'exigence de
dégradation progressive et un blocage au boot sont incompatibles. Elle est levée au moment
où une écriture protégée est tentée.

---

## 4. Dégradation progressive

```
            expiresOn          expiresOn + graceDays
  ─────────────┼───────────────────────┼─────────────────►
     ACTIVE    │        GRACE          │    READ_ONLY
   tout permis │  tout permis + alerte │  écritures refusées
```

Annotez les méthodes de service qui créent des enregistrements :

```java
@Service
public class SaleService {

    @RequiresActiveLicense
    public Sale record(Sale sale) { ... }          // bloqué en lecture seule

    public List<Sale> findByDay(LocalDate day) { ... }   // toujours disponible
}
```

L'annotation fonctionne aussi au niveau classe. Appliquée par un proxy Spring AOP : elle ne
concerne que les appels venant de l'extérieur du bean — annotez le point d'entrée que les
contrôleurs appellent réellement.

À annoter au fur et à mesure de leur écriture : `SaleService`, `InvoiceService`,
`StockMovementService`, `SupplyService`, `CashRemittanceService`. Les services de
consultation et l'authentification restent volontairement non annotés.

---

## 4 bis. Période d'essai — accordée une seule fois

Sans fichier `.lic`, l'application ne bloque plus : elle accorde **90 jours d'essai**
(`TRIAL`), puis passe en lecture seule (`TRIAL_EXPIRED`). Les deux états sont distincts de
`ACTIVE` / `READ_ONLY` : `GET /api/license/status` renvoie `licensed: false` pendant
l'essai, l'interface ne doit jamais présenter une machine non licenciée comme licenciée.

```
         1er lancement         +90 jours
  ────────────┼───────────────────┼──────────────────►
              │      TRIAL        │   TRIAL_EXPIRED
              │   tout permis     │  écritures refusées
```

**C'est la date du premier lancement qui compte, pas celle de la suppression du `.lic`.**
Elle est enregistrée à *chaque* démarrage, y compris quand la licence est valide. Un client
licencié qui vide son dossier `license/` un an plus tard retombe donc directement en
lecture seule : son essai a été consommé le jour de l'installation.

### Où l'enregistrement est stocké

`TrialRegistry` écrit le **même** enregistrement partout où il peut :

| Emplacement | Windows | Linux | macOS |
|---|---|---|---|
| Base de données | table `ls_install_marker` | idem | idem |
| À côté de la licence | `license\.license-trial` (caché) | `license/.license-trial` | idem |
| Profil utilisateur | `%LOCALAPPDATA%\LibertyShop\.trial` | `~/LibertyShop/.trial`, `~/.config/LibertyShop/.trial`, `~/.local/share/LibertyShop/.trial` | `~/LibertyShop/.trial`, `~/Library/Application Support/LibertyShop/.trial` |
| Données machine | `%ProgramData%\LibertyShop\.trial` | `/var/lib/LibertyShop/.trial` | `/usr/local/var/LibertyShop/.trial` |
| Base de registre | `HKCU\Software\LibertyShop\InstallMarker` | — | — |

Les variables `XDG_CONFIG_HOME` et `XDG_DATA_HOME` sont respectées quand le bureau en
définit ; les doublons (deux variables pointant au même endroit) sont éliminés.

### Pourquoi la base de données est la copie qui compte

Les copies fichier et registre ne résistent pas à l'outillage. **Process Monitor** filtré
sur `java.exe`, ou un simple diff **RegShot**, affiche en trois minutes tous les chemins
touchés au premier démarrage — l'attribut « caché » n'y change rien. Multiplier les
répertoires ne fait donc que transformer un `del` en un script de quatre lignes,
rejouable tous les 90 jours.

La base est d'une autre nature : c'est là que vivent les ventes, le stock et les factures
du client. On peut parfaitement la voir depuis ProcMon — mais l'effacer coûte au client
son propre historique, et c'est un prix qu'aucun script ne paie à sa place. C'est le seul
levier dissuasif dont dispose un dispositif sans serveur.

Détails d'implémentation (`TrialRegistry.DatabaseTrialStore`) :

- table créée à la demande en JDBC brut, **pas** en entité JPA — le contrôle ne dépend
  ainsi d'aucun choix de mapping ni du cycle de vie d'Hibernate ;
- SQL portable H2 / MySQL / PostgreSQL : `CREATE TABLE IF NOT EXISTS`, puis `UPDATE`
  suivi d'un `INSERT` si rien n'a été mis à jour, plutôt qu'un *upsert* propre à un dialecte ;
- même enregistrement HMAC que les fichiers : une ligne reprise de la base d'un collègue
  échoue exactement comme un fichier copié ;
- accès paresseux à la `DataSource` — la vérification la plus précoce tourne avant tout
  bean, il n'y a alors pas de pool et la copie base est simplement sautée ;
- toute erreur SQL est une ligne de debug : une base indisponible, c'est une copie de
  moins, jamais une caisse bloquée ;
- une base vide n'est **pas** comptée comme une altération : elle l'est légitimement au
  premier lancement, après une restauration de sauvegarde, après une migration, et à
  chaque démarrage en développement où elle est en mémoire. La compter aurait fait hurler
  l'alerte si souvent que plus personne ne l'aurait lue le jour utile.

Au démarrage, **toutes les copies sont relues** :

- la **plus ancienne date valide** l'emporte — une copie effacée ou réécrite ne peut que
  raccourcir l'essai, jamais le prolonger ;
- toute copie manquante ou altérée est **restaurée** à partir des survivantes, et
  **journalisée** : `Trial records tampered with: 2 missing, 0 altered, 1 intact.` C'est la
  ligne qui dit au support que quelqu'un est allé fouiller dans les fichiers ;
- la restauration marche dans les deux sens : une base recréée vide reprend la date depuis
  les fichiers, et des fichiers effacés la reprennent depuis la base ;
- une date située **dans le futur** (l'essai qui ne finit jamais) est ramenée à
  aujourd'hui et réécrite.

### Pourquoi une copie importée ne passe pas

Chaque enregistrement est authentifié par un HMAC dont la clé dérive de trois éléments :
un sel compilé dans l'application, la clé publique embarquée et **l'empreinte machine**.
Un enregistrement écrit à la main, ou copié depuis un poste encore en essai, ne vérifie
donc pas sur cette machine : il est rejeté et réécrit, jamais cru. C'est aussi ce lien avec
l'empreinte qui rend l'altération *détectable* plutôt que simplement difficile.

Le sel compilé est nécessaire parce que les deux autres entrées sont publiques : la clé
publique est dans le jar livré et l'empreinte est affichée dans l'interface. Sans lui,
n'importe qui pourrait recalculer un HMAC valide sans décompiler quoi que ce soit.

---

## 5. Configuration

> ⚠️ **Ces réglages ne sont lus que depuis le fichier livré dans le jar.**
> Spring Boot donne normalement la priorité aux sources externes, ce qui rendait le
> dispositif désarmable en une ligne :
>
> ```bash
> java -jar backend.jar --liberoshop.license.enabled=false
> LIBEROSHOP_LICENSE_ENABLED=false java -jar backend.jar
> echo liberoshop.license.enabled=false > ./config/application.properties
> ```
>
> `LicensePropertyGuard` s'exécute avant toute lecture et **restaure la valeur du jar**
> pour `enabled`, `clock-guard.enabled` et `trial.system-wide` dès que la clé provient
> d'ailleurs, en le journalisant. Modifier ces valeurs suppose donc de repackager
> l'application — c'est-à-dire le niveau d'effort déjà déclaré hors périmètre en §10.
>
> La durée d'essai est traitée à part, par un plafond compilé
> (`TrialProperties.MAX_DAYS = 90`) : `trial.days` peut la **raccourcir**, jamais
> l'allonger. Un `--liberoshop.license.trial.days=2000` est ramené à 90 sans bruit.
>
> Les **chemins** ne sont volontairement pas protégés : relocaliser le dossier de licence
> est un choix de déploiement légitime, et cela ne rapporte rien — la date d'essai vit à
> plusieurs endroits et c'est la plus ancienne qui gagne.

Le projet utilise `application.properties` ; les valeurs réelles y sont déjà. Voici
l'équivalent YAML si vous basculez un jour :

```yaml
liberoshop:
  license:
    # Interrupteur principal. Doit rester true sur une installation client.
    enabled: true

    # Emplacement du fichier signé (relatif au répertoire de lancement).
    path: ./license/libero-shop.lic

    # Mémorise la date la plus avancée jamais vue : reculer l'horloge ne prolonge rien.
    clock-guard:
      enabled: true
      # path: ./license/.license-state   # par défaut, à côté du fichier de licence

    # Essai accordé à une installation sans licence. enabled: false = comportement
    # strict, l'application refuse de démarrer sans .lic.
    trial:
      enabled: true
      days: 90
      # Réplication des copies FICHIER hors du dossier d'installation (profil,
      # ProgramData, XDG, registre). La copie en base est écrite dans tous les cas :
      # elle ne dépend d'aucun système de fichiers et c'est la seule qui dissuade.
      system-wide: true
      # path: ./license/.license-trial   # par défaut, à côté du fichier de licence

    # Renouvellement en ligne. Désactivé tant que votre serveur n'est pas déployé.
    renewal:
      enabled: false
      endpoint: https://licences.houssen.mg/api/v1/renew
      interval-days: 21        # rythme normal
      urgent-within-days: 30   # à moins de 30 jours de l'expiration : essai quotidien
      timeout: 10s
```

En développement, désactivez le contrôle **dans le fichier du classpath** —
`src/main/resources/application.properties`, ou `src/test/resources` où c'est déjà fait.
Passer `--liberoshop.license.enabled=false` en argument de lancement ne fonctionne plus :
c'est précisément ce que le garde-fou annule.

---

## 6. Renouvellement en ligne

Tentative périodique, jamais bloquante :

- une tentative tous les `interval-days` (21 par défaut) en régime normal ;
- une tentative **par jour** dès que l'expiration est à moins de `urgent-within-days` ;
- tout échec réseau est une ligne de debug — la licence locale reste en vigueur ;
- la licence téléchargée est **vérifiée avant** d'être écrite sur disque ;
- une licence qui n'expire pas plus tard que l'actuelle est refusée (anti-rejeu) ;
- écriture atomique via fichier temporaire, avec sauvegarde `.bak`.

Une boutique coupée du réseau pendant un mois renouvelle donc dès le premier jour de retour.

### Contrat attendu de votre serveur

`POST {endpoint}`, corps :

```json
{
  "licenseId": "CUST-0042-2027-...",
  "customerId": "CUST-0042",
  "machineFingerprint": "LS1-4KQ8T-9WZ2M-H7PXR-C3NVB",
  "currentExpiry": "2027-09-24"
}
```

Réponse : le contenu d'un fichier `.lic` renouvelé, ou **un corps vide** si le client n'a
pas encore payé. Le serveur doit signer pour l'empreinte reçue.

---

## 6 bis. Code de renouvellement — le canal hors ligne

Commander une licence se faisait en lisant l'empreinte machine au téléphone. Ça marche,
mais ça ne vous dit que **quelle** machine demande — ni ce qu'elle a déjà, ni depuis quand.
Le reste se retrouvait à la main, en particulier la date d'expiration précédente dont
`--starts-on` a besoin. C'est là que naissaient les erreurs : un `--starts-on` oublié offre
des jours, un `--starts-on` erroné en vole.

Le client envoie donc un **code** qui porte le contexte :

```
GET /api/license/renewal-code
→ LSR1-04JEX-397KW-AJ4YV-Q1GEQ-DC021-C3GK6-RBNQ5-FWBSY-4CS78
```

| Décalage | Taille | Champ |
|---|---|---|
| 0 | 1 | version du format |
| 1 | 13 | empreinte machine, 100 bits compactés |
| 14 | 1 | état courant (1 ACTIVE, 2 GRACE, 3 READ_ONLY, 4 TRIAL, 5 TRIAL_EXPIRED, 0 inconnu) |
| 15 | 2 | expiration courante, en jours depuis 2020-01-01 (`0xFFFF` = aucune) |
| 17 | 2 | jour de production du code, même encodage |
| 19 | 4 | aléa, pour distinguer deux demandes du même jour |
| 23 | 5 | somme de contrôle |

Le tout en base32 Crockford, groupes de cinq, 58 caractères : trop long pour le téléphone,
juste ce qu'il faut pour un copier-coller, une photo ou un QR code.

**La date de production vient de la date corrigée par `ClockGuard`**, donc un code ne peut
pas être rendu plus frais qu'il n'est en avançant puis reculant l'horloge.

**Un essai ne transmet aucune expiration** (`0xFFFF`). Une expiration d'essai est forgée
localement ; la laisser passer ferait démarrer une période payante à une date que la machine
s'est inventée. Le générateur affiche alors `none -- this is a first license`.

### Ce que la somme de contrôle est, et ce qu'elle n'est pas

Elle attrape une faute de recopie — un groupe sauté, deux caractères permutés, un caractère
faux. **Elle n'est pas une signature et le code ne porte aucune autorité.** Elle n'utilise
délibérément aucun secret : en vérifier un supposerait d'embarquer une clé partagée dans
`tools/license-generator`, pour presque rien. Un client qui modifie son code pour annoncer
une expiration plus tardive n'y gagne rien — vous émettez ce qui a été payé, et `install`
refuse déjà une licence qui n'expire pas plus tard que l'installée. L'autorité, c'est la
signature Ed25519 du `.lic` qui repart, et c'est le seul endroit où elle a besoin d'être.

Deux points trouvés en vérifiant les deux implémentations l'une contre l'autre, et corrigés :

- **la somme de contrôle est vérifiée avant la version.** Un caractère corrompu peut tomber
  sur l'octet de version, et un message « version 32 non supportée » envoyait chercher une
  mise à jour d'outil alors que le client avait juste mal recopié ;
- **les bits de bourrage du dernier caractère doivent être nuls.** Le dernier caractère porte
  quatre bits utiles et un bit libre : tant qu'il l'était, chaque code avait un jumeau qui se
  décodait à l'identique, et un client qui tapait le dernier caractère de travers passait la
  somme de contrôle sans que rien ne le signale — une licence était émise. L'encodage est
  canonique maintenant.

`O` est lu comme `0`, `I` et `L` comme `1`, conformément à Crockford : ce sont exactement
les lettres que l'alphabet exclut parce qu'on les substitue, donc les réparer vaut mieux que
refuser un code par ailleurs correct. Le préfixe `LSR1`, lui, reste strict — il se copie tel
quel et mérite son propre message d'erreur.

### Le format est écrit deux fois, exprès

`RenewalCode` côté client, `RenewalCodeReader` côté générateur. Duplication assumée : le
module outil doit rester sans dépendance et ne jamais tirer le backend — c'est cette
séparation qui garantit que la clé privée ne peut pas se retrouver dans le jar du client, et
elle vaut plus que les quarante lignes économisées. Le javadoc de `RenewalCode` est le
contrat ; les deux côtés sont confrontés par le test de round-trip et par une émission réelle
avant livraison.

## 7. Endpoints

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/api/license/status` | Bandeau de renouvellement dans l'interface |
| `GET` | `/api/license/renewal-code` | **Code à envoyer à l'éditeur pour commander ou renouveler** (§6 bis) |
| `GET` | `/api/license/fingerprint` | Empreinte seule, pour une commande dictée au téléphone |
| `POST` | `/api/license/install` | Installer un `.lic` reçu par e-mail (boutique hors ligne) |
| `POST` | `/api/license/renew` | Forcer une tentative après paiement |

`install` est sans risque : le contenu doit porter une signature valide **pour cette
machine** et expirer plus tard que la licence déjà installée.

---

## 8. Empreinte machine

Format lisible au téléphone : `LS1-XXXXX-XXXXX-XXXXX-XXXXX`, alphabet Crockford base32
(ni I, ni L, ni O, ni U — pas de confusion entre 1/I et 0/O). 100 bits d'un SHA-256 ; ni le
nom de machine ni le numéro de série ne quittent le poste.

| OS | Source | Repli |
|---|---|---|
| Windows | `vol %SystemDrive%` (extraction par motif, insensible à la langue) | PowerShell CIM `Win32_LogicalDisk` |
| Linux | `/etc/machine-id` | `/var/lib/dbus/machine-id`, puis `findmnt -no UUID /` |
| macOS | `IOPlatformUUID` | — |

`wmic` n'est volontairement pas utilisé : il est déprécié et déjà absent des Windows 11
récents.

L'empreinte change si le client réinstalle Windows ou renomme le poste — c'est précisément
le moment où une réémission de licence est légitime. Émettez alors une nouvelle licence, ou
prévoyez plusieurs `--fingerprint` dès le départ pour un client multi-caisses.

---

## 9. Tests

```bash
cd backend && ./mvnw test
```

76 tests, dont : signature valide, charge utile modifiée après signature, signature par une
clé étrangère, algorithme et format inattendus, fichier corrompu, machine différente,
expiration, période de grâce, bascule en lecture seule, refus d'une licence rejouée,
protection contre le recul d'horloge, fichier d'état copié d'une autre installation,
round-trip UTF-8 des noms accentués, et application effective de `@RequiresActiveLicense`.

Côté essai : octroi sur installation neuve, fin après la durée configurée, restauration des
enregistrements supprimés, date la plus ancienne retenue, rejet d'un enregistrement
modifié à la main ou provenant d'une autre machine, date future ramenée à aujourd'hui,
suppression d'une licence payée qui ne rouvre pas d'essai, et installation d'une première
licence pendant l'essai.

Côté base de données, sur une vraie H2 en mémoire : l'essai survit à l'effacement de
**tous** les fichiers, une ligne provenant d'une autre machine est rejetée, une base
recréée vide est réalimentée depuis les fichiers, et une installation sans base du tout
fonctionne normalement.

Côté configuration : un essai de 2000 jours ramené au plafond, une durée raccourcie
acceptée, une durée nulle ou négative refusée, et — pour chacun des trois interrupteurs —
la valeur du jar restaurée face à un argument de ligne de commande, le repli sûr quand le
jar ne dit rien, et la valeur du jar respectée quand elle vient bien de là.

Côté code de renouvellement : round-trip de tous les champs, essai qui ne transmet pas son
expiration, installation dont l'état est inévaluable, tolérance aux minuscules / espaces /
séparateurs manquants, réparation de `O`/`I`/`L`, rejet d'une permutation, d'un dernier
caractère faux, d'un code tronqué, d'une date hors de portée du format, et longueur stable.

> Les deux implémentations du format sont aussi confrontées **hors tests** : un code produit
> par le client réel est passé au générateur réel, qui doit en tirer la bonne empreinte, le
> bon `--starts-on`, et un `.lic` dont `inspect` dit `Signature: VALID`. C'est ce contrôle,
> et non les tests unitaires, qui a révélé les deux défauts corrigés en §6 bis.

> Les tests de l'essai passent `system-wide: false` : un test qui écrirait dans le profil
> ou dans la base de registre de la machine de build vaudrait moins que pas de test du tout.

> La `DataSource` de test passe par `DriverManager` et ne nomme aucune classe H2 : le
> pilote est une dépendance `runtime`, absent du classpath de compilation des tests.

> **Piège de build rencontré.** Avec Surefire 3.5.6 + JUnit 6, les `@Test` déclarés à la
> racine d'une classe qui contient aussi des `@Nested` **ne sont pas exécutés** (ils passent
> pourtant en sélection directe). `LicenseVerifierTest` place donc tous ses tests dans des
> classes `@Nested`. À garder en tête pour les futurs tests du projet.

---

## 9 bis. Héritage du nom « Liberty Shop »

Le produit s'appelait Liberty Shop. Le renommage en Libero Shop est **volontairement
incomplet** : trois choses gardent l'ancien nom, et les changer casserait des installations
déjà payées ou rendrait des protections inopérantes.

| Ce qui garde l'ancien nom | Où | Conséquence si on le renommait |
|---|---|---|
| Contextes de dérivation HMAC `liberty-shop/{clock-guard,trial,renewal-code}/v1` | `ClockGuard`, `TrialRegistry`, `RenewalCode` + `RenewalCodeReader` côté éditeur | Tous les fichiers d'état et d'essai déjà écrits échouent à leur contrôle d'intégrité et sont ignorés : la protection anti-recul d'horloge repart de zéro et **un nouvel essai de 90 jours est accordé** à une machine qui a consommé le sien |
| Répertoires et clé de registre `LibertyShop` | `TrialRegistry` (profil utilisateur, données machine, `HKCU\Software\LibertyShop`) | Les marqueurs existants ne sont plus trouvés : même effet, un essai qui recommence |
| Discriminant `"format": "liberty-shop-license"` | `LicenseFile.LEGACY_FORMAT` | **Accepté en lecture**, jamais écrit. Une licence vendue sous l'ancien nom reste valable ; le générateur n'émet plus que `libero-shop-license` |

Deux compatibilités s'ajoutent, elles aussi en lecture seule :

- **Nom du fichier** : si `libero-shop.lic` est absent, `LicenseService` lit
  `liberty-shop.lic` dans le même répertoire et le journalise. Le prochain renouvellement est
  écrit sous le nouveau nom, donc la reprise se fait d'elle-même.
- **Préfixe des propriétés** : `liberoshop.*` a remplacé `libertyshop.*`. Un
  `application.properties` externe qui utilisait encore l'ancien préfixe n'est plus lu — sans
  effet sur la licence, dont les interrupteurs ne viennent que du jar (§5), mais pensez au
  `liberoshop.security.jwt.secret` d'une installation client.

---

## 10. Limites assumées

- **La clé publique embarquée peut être remplacée** par quelqu'un qui décompile, modifie et
  recompile le jar. C'est une limite intrinsèque à toute protection côté client en langage
  managé ; aucune obfuscation ne la supprime, elle ne fait qu'en augmenter le coût.
  La cible réaliste ici est le client qui copie le dossier sur une deuxième caisse ou
  recule l'horloge — pas l'ingénieur inverse déterminé.
- **Supprimer le fichier d'état d'horloge puis reculer l'horloge** ramène une licence
  expirée à l'état actif : sans fichier, `ClockGuard` n'a plus de « date la plus avancée »
  à opposer et évalue la date système telle quelle. Contrairement à ce que disait une
  version antérieure de cette section, l'effacement n'est donc *pas* sans effet. Le frein
  est commercial plutôt que technique — une caisse à la mauvaise date fausse les tickets et
  les dates de vente. À corriger en répliquant l'état d'horloge dans les mêmes supports que
  l'essai, la base comprise ; il est aujourd'hui seul dans `license/.license-state`.
- **Effacer tous les enregistrements d'essai à la fois** rouvre un essai. C'est le prix d'un
  dispositif entièrement hors ligne : sans serveur, la machine ne peut pas se souvenir de ce
  qui a été effacé partout. Deux nuances depuis l'ajout de la copie en base : les
  emplacements fichier se découvrent en trois minutes avec Process Monitor et s'effacent par
  script, alors que la copie en base ne part qu'avec les ventes et le stock du client.
  Chaque suppression partielle est détectée, restaurée et journalisée, et le vrai levier
  reste la licence signée.
- **La durée d'essai et les interrupteurs ne sont plus modifiables depuis l'extérieur**
  (§5), mais ils le restent pour qui repackage le jar. C'est la même limite que la clé
  publique embarquée, et elle se traite au même endroit : nulle part.
- **Aucune authentification sur `/api/license/*`** pour l'instant, le projet n'ayant pas
  encore de sécurité HTTP. Aucun de ces endpoints ne peut affaiblir la licence, mais pensez
  à les intégrer à vos règles d'accès quand Spring Security arrivera.
