# Libero Shop — journal de réalisation

Ce document détaille, partie par partie, ce qui a été réalisé à partir de
[`TODO.md`](TODO.md) : ce qui change pour l'utilisateur, les fichiers touchés, les choix faits,
ce qui a été vérifié et ce qui reste ouvert.

Chaque partie est livrée seule, relue, puis commitée avant de passer à la suivante.

| Partie | Sujet | État |
|---|---|---|
| 1 | Configuration de production : données de démo et secret JWT | à relire |
| 2 | Droits : rôles relus en base, révocation, lecture par rôle | à faire |
| 3 | Argent : doubles paiements et vente en double | à faire |
| 4 | Retours clients | en attente d'une décision |
| 5 | HTTPS ou Wi-Fi séparé | décision d'installation |
| 6 | Pagination et Flyway | à faire |

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
