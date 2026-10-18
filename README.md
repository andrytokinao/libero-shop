# Libero Shop

Logiciel de caisse et de gestion de stock pour une boutique : vente au comptoir, commandes,
dépôt, réceptions de marchandise, corrections et inventaires, prix et bénéfices, sauvegardes.

## Contenu du dépôt

| Dossier | Contenu |
|---|---|
| `backend/` | Serveur : Spring Boot 4.1, Java 21, JPA (H2 par défaut), Spring Security (JWT), WebSocket |
| `ui/` | Écrans : Angular 18 ; `ui/android/` pour l'application téléphone (Capacitor) |
| `docs/` | Liste de travail (`TODO.md`, `todo-ui.md`), conceptions et comptes rendus de réalisation en HTML |
| `tools/` | Outils de l'éditeur livrés avec le dépôt (signature des mises à jour) |

## Démarrer en développement

Serveur (le JDK 21 doit être celui de `JAVA_HOME`) :

    cd backend
    ./mvnw -DskipFrontend=true spring-boot:run -Dspring-boot.run.profiles=dev

Écrans, dans un second terminal :

    cd ui
    npm install
    npx ng serve

Le profil `dev` charge une boutique de démonstration. Sans lui, une installation neuve demande
de créer le premier administrateur.

## Tests

    cd backend && ./mvnw -o -DskipFrontend=true test
    cd ui && npx ng test --watch=false

Les règles métier (prix, coûts, unités, stock, argent) sont testées dans
`backend/src/test/java/.../service/`.

## Pour aller plus loin

- `CLAUDE.md` : conventions du code et façon de travailler sur ce dépôt.
- `docs/CONCEPTION-prix-et-unites.md` : à lire avant de toucher aux prix ou aux unités.
- `backend/LICENSE-MODULE.md` : le fonctionnement des licences.
- `docs/` : un compte rendu par point réalisé, à ouvrir dans un navigateur.
