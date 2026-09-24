# Module de licence — Liberty Shop

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

### Format du fichier `.lic`

Une enveloppe JSON contenant la charge utile **encodée**, plus sa signature détachée :

```json
{
  "format": "liberty-shop-license",
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

**Renouvellement.** Passez `--starts-on <date d'expiration précédente>` pour que le client
ne perde pas les jours déjà payés :

```bash
java -jar target/license-generator.jar issue \
     --private-key ./keys/license-private.key \
     --customer-id CUST-0042 --customer-name "Supermarche Houssen Analakely" \
     --fingerprint LS1-4KQ8T-9WZ2M-H7PXR-C3NVB \
     --plan MONTHLY --starts-on 2027-09-24 \
     --out ./out/CUST-0042-renouvellement.lic
```

Options utiles : `--plan MONTHLY|ANNUAL|TRIAL`, `--months <n>`, `--expires-on`,
`--grace-days <n>`, `--fingerprint` répétable (plusieurs caisses), `--notes`.

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
   - **Signature invalide / mauvaise machine / fichier absent** → le démarrage est refusé,
     avec un encadré lisible en console indiquant le chemin attendu et l'empreinte machine.
   - **Expirée** → l'application démarre en avertissant, puis dégrade.
4. `LicenseService` refait la vérification dans le contexte Spring (quelques
   millisecondes), ce qui couvre les chemins qui ne passent pas par `main()`.

Le listener est enregistré explicitement dans `BackendApplication.main` plutôt que via
`spring.factories` : le câblage reste visible en un seul endroit, et surtout `@SpringBootTest`
(qui n'appelle pas `main()`) n'exige pas de vraie licence sur la machine de build.

### Exceptions

| Exception | Code | Moment | Effet |
|---|---|---|---|
| `LicenseNotFoundException` | `LICENSE_NOT_FOUND` | Démarrage | Démarrage refusé |
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

## 5. Configuration

Le projet utilise `application.properties` ; les valeurs réelles y sont déjà. Voici
l'équivalent YAML si vous basculez un jour :

```yaml
libertyshop:
  license:
    # Interrupteur principal. Doit rester true sur une installation client.
    enabled: true

    # Emplacement du fichier signé (relatif au répertoire de lancement).
    path: ./license/liberty-shop.lic

    # Mémorise la date la plus avancée jamais vue : reculer l'horloge ne prolonge rien.
    clock-guard:
      enabled: true
      # path: ./license/.license-state   # par défaut, à côté du fichier de licence

    # Renouvellement en ligne. Désactivé tant que votre serveur n'est pas déployé.
    renewal:
      enabled: false
      endpoint: https://licences.houssen.mg/api/v1/renew
      interval-days: 21        # rythme normal
      urgent-within-days: 30   # à moins de 30 jours de l'expiration : essai quotidien
      timeout: 10s
```

En développement, désactivez simplement le contrôle :
`libertyshop.license.enabled=false` (c'est déjà le cas dans `src/test/resources`).

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

## 7. Endpoints

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/api/license/status` | Bandeau de renouvellement dans l'interface |
| `GET` | `/api/license/fingerprint` | Empreinte à communiquer à l'éditeur |
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

38 tests, dont : signature valide, charge utile modifiée après signature, signature par une
clé étrangère, algorithme et format inattendus, fichier corrompu, machine différente,
expiration, période de grâce, bascule en lecture seule, refus d'une licence rejouée,
protection contre le recul d'horloge, fichier d'état copié d'une autre installation,
round-trip UTF-8 des noms accentués, et application effective de `@RequiresActiveLicense`.

> **Piège de build rencontré.** Avec Surefire 3.5.6 + JUnit 6, les `@Test` déclarés à la
> racine d'une classe qui contient aussi des `@Nested` **ne sont pas exécutés** (ils passent
> pourtant en sélection directe). `LicenseVerifierTest` place donc tous ses tests dans des
> classes `@Nested`. À garder en tête pour les futurs tests du projet.

---

## 10. Limites assumées

- **La clé publique embarquée peut être remplacée** par quelqu'un qui décompile, modifie et
  recompile le jar. C'est une limite intrinsèque à toute protection côté client en langage
  managé ; aucune obfuscation ne la supprime, elle ne fait qu'en augmenter le coût.
  La cible réaliste ici est le client qui copie le dossier sur une deuxième caisse ou
  recule l'horloge — pas l'ingénieur inverse déterminé.
- **Supprimer le fichier d'état d'horloge** est possible : cela efface l'historique, mais ne
  peut jamais rendre une licence plus jeune que sa propre date d'expiration.
- **Aucune authentification sur `/api/license/*`** pour l'instant, le projet n'ayant pas
  encore de sécurité HTTP. Aucun de ces endpoints ne peut affaiblir la licence, mais pensez
  à les intégrer à vos règles d'accès quand Spring Security arrivera.
