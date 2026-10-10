# Libero Shop — journal de réalisation

Ce document détaille, partie par partie, ce qui a été réalisé à partir de
[`TODO.md`](TODO.md) : ce qui change pour l'utilisateur, les fichiers touchés, les choix faits,
ce qui a été vérifié et ce qui reste ouvert.

Chaque partie est livrée seule, relue, puis commitée avant de passer à la suivante. Les parties
1 et 2 sont ci-dessous, séparées par une ligne `---` ; à partir de la partie 3, chaque partie a
son propre fichier, nommé d'après la fonctionnalité et lié depuis le tableau.

| Partie | Sujet | État |
|---|---|---|
| 1 | Configuration de production : données de démo et secret JWT | commitée (`7b6744b`) |
| 2 | Droits : rôles relus en base, révocation, lecture par rôle | commitée (`0c4f6d3`) |
| 3 | Argent : doubles paiements et vente en double | faite — voir [`REALISATION-partie-3-argent.md`](REALISATION-partie-3-argent.md) |
| 4 | Retours clients | en attente d'une décision |
| 5 | HTTPS ou Wi-Fi séparé | décision d'installation |
| 6 | Pagination et Flyway | en cours — sorties de stock : [`04-sorties-de-stock-par-pages.md`](04-sorties-de-stock-par-pages.md) (`c9c65d8`) ; factures : [`05-factures-par-periode.md`](05-factures-par-periode.md) ; Flyway à faire |

---

## Partie 1 — Configuration de production : données de démo et secret JWT

Réalisée le 10 octobre 2026. Points de `TODO.md` couverts : section 1, « Données de démo actives
dans le fichier livré » et « Secret JWT vide ».

### Ce qui change

**Avant.** L'application livrée créait à son premier démarrage sept comptes de démonstration,
dont le super-admin `mparany`, tous avec le mot de passe `vaha2026` écrit dans le dépôt. Et la
clé qui signe les sessions était tirée au hasard à chaque démarrage : un redémarrage du PC
déconnectait toutes les caisses.

**Après.**

1. **Une installation neuve démarre sans aucun compte.** Sa page de connexion laisse la place à
   un écran « Première utilisation » où l'on crée le compte administrateur.
2. **Ce premier compte ne se crée que sur l'ordinateur du serveur**, une seule fois. Depuis un
   téléphone ou un autre poste, l'écran explique où aller.
3. **Chaque installation génère sa propre clé de signature** au premier démarrage et la conserve
   dans un fichier. Un redémarrage ne déconnecte plus personne.
4. **Les données de démo existent toujours**, mais seulement avec le profil `dev`.

### 1. Clé de signature des sessions (JWT)

Trois sources, essayées dans cet ordre :

| Source | Réglage | Pour qui |
|---|---|---|
| Secret écrit dans la configuration | variable d'environnement `LIBEROSHOP_JWT_SECRET` (32 caractères au moins) | l'administrateur d'une machine qui veut tenir lui-même le secret |
| Fichier généré par l'installation | `liberoshop.security.jwt.secret-file` | **le cas normal** : rien à faire à la main |
| Clé aléatoire à chaque démarrage | aucun des deux | les tests uniquement ; un avertissement est journalisé |

Emplacement du fichier :

- en développement : `./data/jwt-secret.key` (dossier déjà ignoré par git) ;
- application installée : `~/.libertyshop/security/jwt-secret.key`, réglé dans
  `backend/src/main/jpackage/runtime.args`. Il est volontairement hors de `data/`, qu'une
  restauration de sauvegarde remplace : restaurer ne doit pas déconnecter tout le monde.

Comportements à connaître :

- **Supprimer le fichier déconnecte tout le monde** : le démarrage suivant en génère un autre.
  C'est la manière prévue de forcer une reconnexion générale.
- **Fichier tronqué ou vide** (disque plein, coupure) : il est remplacé, avec un avertissement
  dans le journal, plutôt que d'empêcher la boutique d'ouvrir.
- **Emplacement inutilisable** (dossier en lecture seule) : l'application démarre quand même avec
  une clé du jour et journalise une erreur.
- Le fichier est écrit à côté puis déplacé en une fois : une coupure ne laisse jamais une demi-clé.
- Il est **aussi secret que la base de données** : qui le lit peut fabriquer une session de
  n'importe quel compte. Ne jamais le commiter ni l'envoyer par e-mail.

Fichiers :

- `security/JwtSecretFile.java` (nouveau) — lecture ou création de la clé.
- `security/JwtProperties.java` — nouveau champ `secretFile`.
- `security/JwtConfiguration.java` — choix de la source.
- `resources/application.properties`, `jpackage/runtime.args` — réglages.

### 2. Données de démonstration

- `application.properties` : `liberoshop.demo-data.enabled=false`. Le mot de passe `vaha2026`
  n'y figure plus.
- `application-dev.properties` (nouveau) : réactive le jeu de données, uniquement avec le profil
  `dev`.

Pour retrouver les comptes de démo en développement :

```
mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

ou, dans IntelliJ, « Active profiles » = `dev` sur la configuration `BackendApplication`.

Le jeu de données n'est toujours inséré que dans une base vide. **Une base de développement
existante n'est pas touchée** : ses comptes restent tels quels.

### 3. Premier compte administrateur

Sans données de démo, plus personne ne peut se connecter à une installation neuve. D'où ce
nouvel écran, qui n'était pas dans la liste d'origine mais sans lequel le point précédent
rendrait l'application inutilisable.

Côté serveur :

| Appel | Accès | Rôle |
|---|---|---|
| `GET /api/setup/status` | public | répond `required` (aucun compte n'existe) et `allowedHere` (cet appareil peut le créer) |
| `POST /api/setup/admin` | public, mais voir ci-dessous | crée le premier compte, toujours super-admin |

Règles appliquées par `SetupController` et `InitialSetupService` :

- **Une seule fois.** Dès qu'un compte existe, quel que soit son rôle, la création répond 409
  `SETUP_ALREADY_DONE`. Les comptes suivants se créent comme avant, par un super-admin.
- **Seulement depuis l'ordinateur du serveur** (adresse de bouclage). Sinon 409
  `SETUP_ONLY_ON_SERVER`. Raison : tant qu'aucun compte n'existe, n'importe qui sur le Wi-Fi de
  la boutique pourrait arriver le premier et se nommer administrateur.
- **Un relais sur la machine ne compte pas comme le serveur.** Une requête portant
  `X-Forwarded-For`, `X-Real-IP` ou `Forwarded` est refusée : derrière un tunnel ou un reverse
  proxy, tout appelant semble local.
- **Mot de passe de 8 caractères au moins** pour ce compte (les autres comptes restent à 6, voir
  « Reste ouvert »).
- Deux créations simultanées ne peuvent pas passer toutes les deux (méthode synchronisée).

Côté écran :

- `features/auth/first-admin.component.ts` (nouveau), route `/installation`.
- `firstAccountGuard` sur `/login` : interroge le serveur et redirige vers `/installation` tant
  qu'aucun compte n'existe. La réponse « un compte existe » est mémorisée, la question n'est
  plus posée ensuite.
- Le mot de passe est saisi deux fois : personne ne peut encore le réinitialiser.
- Après création, l'administrateur est connecté directement.
- Si le serveur est injoignable ou plus ancien, la page de connexion s'affiche comme avant.

Fichiers :

- `web/SetupController.java`, `service/InitialSetupService.java`,
  `web/dto/CreateFirstAdminRequest.java` (nouveaux).
- `security/SecurityConfiguration.java` — les deux appels sont publics.
- `ui/src/app/core/api/setup.api.ts`, `features/auth/first-admin.component.ts` (nouveaux).
- `ui/src/app/core/guards/auth.guard.ts`, `app.routes.ts`.

### Vérifications faites

- **Tests automatiques du serveur : 495, tous verts** (`mvnw -o -DskipFrontend=true test`), dont
  11 nouveaux :
  - `JwtSecretFileTest` (7) : création, relecture, deux installations différentes, fichier
    tronqué, priorité du secret configuré, emplacement inutilisable ;
  - `InitialSetupServiceTest` (3) : premier compte super-admin, porte fermée ensuite ;
  - `SetupControllerTest` (1) : le parcours complet sur une base sans démo — refus depuis le
    Wi-Fi, refus derrière un relais, refus d'un mot de passe court, création, connexion, puis
    refus d'une seconde création.
- **Application réelle démarrée avec la vraie configuration**, sur une base temporaire en
  mémoire et le port 8099 (vos bases n'ont pas été touchées) :
  - `GET /api/setup/status` répond `required: true` ;
  - la connexion `mparany` / `vaha2026` est refusée (401) ;
  - le premier compte est créé, puis se connecte ;
  - le fichier de clé est créé (64 caractères) ;
  - après arrêt et redémarrage, le fichier est identique, l'ancien jeton est toujours reconnu
    comme signé par le serveur, et un jeton falsifié est refusé.
- **Interface** : `ng build` passe sans erreur.

### Non vérifié

- **L'écran « Première utilisation » n'a pas été ouvert dans un navigateur.** Il est compilé et
  ses appels serveur sont testés, mais sa mise en page et son parcours à la souris restent à
  voir.
- **L'installeur Windows n'a pas été reconstruit** : le chemin
  `~/.libertyshop/security/jwt-secret.key` de `runtime.args` suit le modèle des trois autres
  chemins du fichier, sans avoir été essayé sur une installation.
- Les tests de l'interface (`ng test`) n'ont pas été lancés.

### Pour essayer

1. Démarrer le serveur sur une base vide (par exemple en renommant temporairement
   `backend/data/`), sans profil.
2. Ouvrir `http://localhost:8080` (ou `http://localhost:4200` avec `ng serve`) : l'écran
   « Première utilisation » doit apparaître à la place de la connexion.
3. Créer le compte : on arrive connecté sur « Vue d'ensemble ».
4. Redémarrer le serveur : la session reste ouverte.
5. Depuis un téléphone sur le Wi-Fi, avant l'étape 3 : l'écran dit de créer le compte sur
   l'ordinateur du serveur.

### Reste ouvert

- **`application-dev.properties` est embarqué dans le jar.** L'activer demande un accès à la
  ligne de commande du poste et n'agit que sur une base vide ; acceptable, mais il peut être
  exclu du build de production.
- **Mot de passe des autres comptes** : toujours 6 caractères au minimum, et un utilisateur ne
  peut pas changer le sien (point déjà dans `TODO.md`).
- **Mot de passe administrateur perdu** : aucun moyen de le réinitialiser sans un autre
  super-admin. À prévoir : une procédure de secours depuis l'ordinateur du serveur.
- **`LICENSE-MODULE.md`** mentionne encore `liberoshop.security.jwt.secret` comme réglage à
  penser sur une installation client ; ce n'est plus nécessaire.

### Hors de cette partie

`catalogue-epicerie-exemple-quantites.xlsx` (à la racine) et `backend/libero-shop.png`
apparaissent dans `git status` : ils ne viennent pas de ce travail et ne sont pas à inclure
dans le commit de cette partie.

---

## Partie 2 — Droits : rôles relus en base, révocation, lecture par rôle

Réalisée le 10 octobre 2026. Points de `TODO.md` couverts : section 1, « Un admin désactivé ou
rétrogradé garde ses droits 12 h », « Aucune révocation de jeton » et « Tout rôle connecté lit
tout ».

### Ce qui change

**Avant.** Le jeton de session portait les rôles et valait 12 heures quoi qu'il arrive : un
compte désactivé continuait à lire, un administrateur rétrogradé restait administrateur, un mot
de passe changé laissait les anciennes sessions ouvertes, et « Déconnexion » ne faisait rien côté
serveur. Par ailleurs tout compte connecté, même une prise de commande, pouvait lister toutes les
factures de la boutique, tous les versements, les fournisseurs et les sorties de stock.

**Après.**

1. **Le compte est relu en base à chaque requête.** Désactiver un compte ou lui retirer un rôle
   prend effet à sa requête suivante, lectures comprises.
2. **Changer un mot de passe ferme toutes les sessions du compte.**
3. **« Déconnexion » ferme réellement la session de l'appareil** ; les autres appareils du même
   compte restent connectés.
4. **Chaque rôle ne lit que ce qui le concerne** (tableau plus bas).

### 1. Compte relu à chaque requête

Le jeton ne dit plus que **qui** est l'appelant (identifiant du compte). Ce qu'il a le droit de
faire est lu dans sa ligne en base à chaque requête, par `AccountAuthenticationConverter`.

| Événement | Avant | Après |
|---|---|---|
| Compte désactivé | actions refusées, lectures possibles 12 h | tout refusé (401) à la requête suivante |
| Rôle retiré | conservé jusqu'à la reconnexion | refusé (403) à la requête suivante |
| Rôle ajouté | reconnexion nécessaire | utilisable tout de suite |

À connaître :

- **Coût** : une lecture du compte par requête, sur une base qui tourne sur le même poste.
- **L'écran suit au rechargement.** Après un changement de rôle, le menu déjà affiché ne change
  pas tout seul : une entrée peut rester visible et répondre « Votre rôle ne vous autorise pas »
  jusqu'au prochain rechargement de la page.
- **Les rôles ne sont plus écrits dans le jeton.** Les jetons émis avant cette version restent
  acceptés : personne n'est déconnecté par la mise à jour.

Fichiers :

- `security/AccountAuthenticationConverter.java` (nouveau) — la relecture du compte.
- `security/JwtService.java`, `security/JwtConfiguration.java`, `security/AppUserDetails.java`,
  `security/SecurityConfiguration.java`, `security/CurrentUser.java`.

### 2. Fermer une session

Deux mécanismes, vérifiés à chaque requête :

| Besoin | Mécanisme | Effet |
|---|---|---|
| Fermer **toutes** les sessions d'un compte | numéro `tokenVersion` sur le compte, recopié dans chaque jeton | un jeton dont le numéro n'est plus celui du compte est refusé |
| Fermer **une** session (un appareil) | table `revoked_token` : l'identifiant du jeton déconnecté | ce jeton-là est refusé, les autres continuent |

Quand :

- **Changement de mot de passe** (`PUT /api/users/{id}/password`) : toutes les sessions du compte
  sont fermées. Un super-admin qui change **son propre** mot de passe est donc déconnecté lui
  aussi, et se reconnecte avec le nouveau.
- **Déconnexion** (`POST /api/auth/logout`) : le jeton de l'appareil est inscrit dans
  `revoked_token`. Un jeton resté dans un navigateur ou dans une sauvegarde de téléphone n'ouvre
  plus rien.
- **Désactivation** : pas besoin de numéro, le compte désactivé est refusé directement.

Choix fait : la déconnexion ne ferme **que l'appareil**, pas tout le compte. Raison : un même
compte peut être ouvert sur deux postes (une caisse partagée) ; fermer l'autre poste lui ferait
perdre son panier en cours, qui n'existe qu'en mémoire.

Base de données :

- colonne `user_app.token_version` (vide sur les comptes existants, lue comme 0) ;
- table `revoked_token` (identifiant du jeton, échéance). Les lignes des jetons expirés sont
  supprimées à chaque déconnexion.

Les deux sont créées automatiquement au démarrage (`ddl-auto=update`), sans migration à écrire.

Fichiers :

- `entity/RevokedToken.java`, `repository/RevokedTokenRepository.java`,
  `security/SessionRevocation.java` (nouveaux).
- `entity/UserApp.java` — `tokenVersion`, `revokeSessions()`.
- `web/AuthController.java` — déconnexion ; `service/UserAccountService.java` — mot de passe.

### 3. Lecture par rôle

| Donnée | Avant | Après |
|---|---|---|
| Fournisseurs (`GET /api/suppliers`) | tout compte connecté | responsable de dépôt, super-admin |
| Sorties de stock (`GET /api/stock/outputs`) | tout compte connecté | responsable de dépôt, super-admin |
| Versements (`GET /api/remittances`) | tout compte connecté | caisse et super-admin : tous ; les autres : seulement ceux qu'ils ont remis |
| Factures (`GET /api/invoices`, `GET /api/invoices/{id}`) | tout compte connecté | selon le rôle, ci-dessous |

Factures, par rôle (`service/InvoiceAccess.java`) :

| Rôle | Ce qu'il lit |
|---|---|
| Super-admin, responsable de dépôt | tout l'historique |
| Caisse, agent de dépôt | les commandes **du jour**, celles **encore ouvertes** (non payées, argent en route, non remises), et **les siennes** de n'importe quelle date |
| Prise de commande | uniquement les commandes qu'il a prises |

Précisions :

- **« Les siennes »** : vendues par lui, préparées ou remises par lui, annulées par lui, ou dont
  l'argent est passé par ses mains (encaissé par lui, ou versement compté par lui à la caisse).
- **Rôles cumulés** : le rôle le plus large l'emporte. Le gérant d'une petite épicerie qui cumule
  tout lit tout.
- **Une liste n'est jamais refusée, elle est réduite** : le même écran sert au caissier et à
  l'administrateur. Une facture demandée par son numéro et hors périmètre répond 403.
- **Pourquoi la caisse et le dépôt voient le jour entier** : le caissier encaisse la commande
  impayée d'un collègue, le magasinier la sert. Ce qui est fermé, c'est l'historique : avant,
  n'importe quel compte pouvait additionner toutes les ventes de la boutique.

Fichiers :

- `service/InvoiceAccess.java` (nouveau), `service/InvoiceService.java` (`searchFor`,
  `findByIdFor`), `repository/PaymentRepository.java`.
- `web/InvoiceController.java`, `web/RemittanceController.java`, `web/SupplierController.java`,
  `web/StockController.java`.

Aucun écran n'a été modifié : chaque écran demandait déjà des données de son propre rôle.

### Vérifications faites

- **Tests automatiques du serveur : 504, tous verts** (`mvnw -o -DskipFrontend=true test`), dont
  9 nouveaux :
  - `AccessRightsTest` (8), à travers la vraie chaîne de sécurité et de vrais jetons : compte
    désactivé coupé à la requête suivante ; rôle retiré puis rôle ajouté sans reconnexion ;
    mot de passe changé qui ferme deux sessions du compte et pas celle de l'administrateur ;
    déconnexion qui ferme un appareil et pas l'autre ; fournisseurs et sorties refusés à la
    caisse, au dépôt et à la prise de commande ; factures réduites selon le rôle (liste et
    lecture par numéro) ; rôle le plus large ; versements ;
  - `JwtServiceTest` : 2 tests remplacés, 1 ajouté (le jeton ne porte plus de rôle, il porte son
    numéro de session).
- **Base créée avant ce changement** : simulée (colonne `token_version` et table `revoked_token`
  retirées d'une base contenant comptes et ventes), puis rouverte avec le nouveau code : les 7
  comptes sont relus, un jeton est accepté, la déconnexion le refuse, un changement de numéro de
  session est enregistré.

### Non vérifié

- **Aucun essai dans un navigateur ni sur téléphone** : le comportement de l'écran quand la
  session est fermée par le serveur (retour à la connexion sur un 401) repose sur le code
  existant de l'intercepteur, qui n'a pas été rejoué à la main.
- **Votre base de développement réelle (`data/libertyshop.mv.db`) n'a pas été essayée** : elle
  était verrouillée par l'application en marche. La vérification ci-dessus porte sur une base
  simulée.
- Les tests de l'interface (`ng test`) et `ng build` n'ont pas été relancés : aucun fichier de
  l'interface n'a changé.

### Pour essayer

1. Se connecter en caissier sur un navigateur, en super-admin sur un autre.
2. Super-admin : désactiver le caissier. Côté caissier, le clic suivant renvoie à la connexion.
3. Réactiver, reconnecter le caissier, puis changer son mot de passe : même effet.
4. Caissier connecté sur deux navigateurs : « Déconnexion » sur l'un, l'autre continue.
5. Caissier : « Mes factures » ne montre que les siennes ; « À encaisser » montre toujours les
   impayés de tout le monde.

### Reste ouvert

- **Les notifications en direct (WebSocket) ne suivent pas ces règles.** Le canal `orders`
  envoie chaque commande modifiée à tous les comptes connectés, quel que soit leur rôle, et une
  connexion déjà ouverte n'est pas coupée quand le compte est désactivé ou la session fermée
  (elle l'est à la reconnexion suivante). Une prise de commande reçoit donc encore, en direct,
  les commandes du jour des autres. À traiter : un canal par périmètre, et la fermeture des
  connexions d'un compte révoqué.
- **Tableaux de bord** (`/api/dashboard/...`) : non revus dans cette partie.
- **Menu après changement de rôle** : suit au rechargement seulement (voir plus haut).
- **`revoked_token`** n'est nettoyée qu'à l'occasion d'une déconnexion ; sans déconnexion
  pendant longtemps, des lignes expirées y restent (sans effet, quelques octets chacune).
