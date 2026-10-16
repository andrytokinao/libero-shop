# liberty-shop

Logiciel de caisse et de gestion de stock pour une boutique.
- `backend/` : Spring Boot 4.1, Java 21, JPA, Spring Security (JWT), WebSocket, Apache POI pour les imports Excel
- `ui/` : Angular 18
- `docs/` : TODO, conceptions et comptes rendus de réalisation

## Commandes

Backend (le JDK n'est pas celui du système) :

    export JAVA_HOME=~/.jdks/ms-21.0.12.1
    cd backend && ./mvnw -o -DskipFrontend=true test

UI (les tests passent par Edge, Chrome n'est pas installé) :

    export CHROME_BIN="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
    cd ui && npx ng test --watch=false

Python n'est pas installé sur la machine : ne pas écrire de scripts Python.

## Façon de travailler

- Traiter `docs/TODO.md` point par point. Ignorer les points marqués « décision attendue » et les signaler à la fin.
- Pour chaque point : coder, faire passer les tests, cocher le point dans `docs/TODO.md`, écrire le compte rendu `docs/NN-fonctionnalite.md` (numéro suivant), commiter, pousser `develop`, puis passer au point suivant.
- Un commit par point. Message en français, sans accents, dans le style de `git log` (ex. « Sorties de stock valorisees au prix de la vente »).
- Avancer sans demander d'autorisation : l'utilisateur relit tout à la fin.
- Répondre en français.

## Conventions du code

- Package racine : `com.houssen.liberoshop` (`entity`, `repository`, `service`, `web`, `web/dto`, `bootstrap`, ...).
- Les migrations de données au démarrage vont dans `bootstrap/` et ont chacune leur test.
- Toute règle métier (prix, coûts, unités, stock) est testée dans `backend/src/test/.../service/`.
- Avant de toucher aux prix ou aux unités, lire `docs/CONCEPTION-prix-et-unites.md`.

## Qualité du code (niveau senior)

### Architecture backend

Architecture en couches, à respecter strictement :
- `web/` : contrôleurs REST minces. Ils valident l'entrée (DTO + `@Valid`), appellent UN service et convertissent le résultat en DTO. Aucune règle métier ici.
- `service/` : toute la logique métier et les transactions (`@Transactional` sur le service, jamais sur le contrôleur).
- `repository/` : accès aux données uniquement, pas de logique.
- `entity/` : les entités ne sortent jamais de la couche service. L'API expose toujours des DTO (`web/dto`, en `record` Java).
- Dépendances à sens unique : web → service → repository. Un service ne connaît ni les DTO web ni `HttpServletRequest`.

Ne pas migrer vers une architecture hexagonale. En revanche, quand un service dépend de l'extérieur (fichiers, réseau, licence, sauvegarde, horloge), passer par une interface (un « port ») pour pouvoir le tester sans cette dépendance.

### SOLID, appliqué concrètement

- **S** : un service = une responsabilité. Au-delà de ~300 lignes ou de plusieurs raisons de changer, le découper (ex. `PurchaseCosting` à part de `CostingService`).
- **O** : pour les variantes de règles (modes de calcul, types d'unité, moyens de paiement), utiliser un pattern Strategy (interface + une implémentation par variante) plutôt qu'un `switch` qui grossit.
- **L** : une implémentation doit respecter le contrat de son interface (pas d'`UnsupportedOperationException`).
- **I** : des interfaces petites et ciblées.
- **D** : injection par constructeur uniquement (champs `final`, pas de `@Autowired` sur les champs). Dépendre d'interfaces pour l'extérieur.

### Design patterns : seulement quand ils servent

- Strategy : règles métier qui varient selon un type.
- Factory / méthode statique `of(...)` : création d'objets complexes ou de DTO.
- Value Object (`record` immuable) : montants, quantités, conversions d'unités. Jamais de `double` pour l'argent : `BigDecimal`, avec un arrondi explicite (`RoundingMode`).
- Pas de pattern « au cas où » : si une seule implémentation est prévue, une classe concrète suffit.

### Règles générales

- Méthodes courtes, noms métier explicites (le vocabulaire de la boutique : vente, sortie, approvisionnement, unité).
- Pas de `null` en retour : `Optional` pour une recherche, une exception métier pour une erreur.
- Exceptions métier dédiées, converties en réponse HTTP par un seul `@RestControllerAdvice`.
- Pas de duplication : avant d'écrire une fonction, chercher si elle existe déjà (`util/`, `service/`).
- Toute règle métier nouvelle ou modifiée a un test unitaire. Nommer les tests par comportement (`sortieValoriseeAuPrixDeVente`).
- Ne pas refactorer du code hors du périmètre du point en cours. Le signaler dans le compte rendu à la place.

### Angular

- Composants « standalone ». La logique et les appels HTTP vont dans des services de `core/`, jamais dans le composant.
- `features/` : un dossier par fonctionnalité. `shared/` : seulement ce qui est réutilisé.
- Types stricts (`models/`), pas de `any`. Désabonnement automatique (`takeUntilDestroyed`, `async` pipe).

## À ne pas faire

- Ne jamais commiter sur `master` : on travaille sur `develop`.
- Ne pas modifier les migrations déjà livrées : en ajouter une nouvelle.
