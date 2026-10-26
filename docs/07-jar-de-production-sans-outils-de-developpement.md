# Moussa Shop — jar de production sans outils de développement

Réalisée le 10 octobre 2026.

Points de [`TODO.md`](TODO.md) concernés : section 1, « `application-dev.properties` embarqué
dans le jar » et « Console H2 livrée dans le jar ».

---

## Ce qui change

**Avant.** Le programme installé chez un client contenait deux choses faites pour le
développeur :

- le fichier de réglages du profil `dev`, qui active les **comptes de démonstration** et contient
  leur mot de passe (`vaha2026`). Il suffisait de démarrer l'application installée avec
  `--spring.profiles.active=dev` sur une base vide pour obtenir un super-admin au mot de passe
  connu ;
- la **console web de la base de données** (H2), éteinte par défaut mais qu'un simple réglage
  pouvait allumer.

**Après.** Le programme fabriqué pour l'installeur ne contient plus ni l'un ni l'autre :

- demander le profil `dev` sur une installation ne change rien : aucun compte de démonstration
  n'est créé, l'écran « Première utilisation » s'affiche ;
- la console de la base n'existe pas dans le programme installé : aucun réglage ne peut
  l'allumer.

**Rien ne change pour le développement** : `mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
et le jar fabriqué sans le profil `installer` gardent les données de démo et la console.

## Détail

Tout est dans `backend/pom.xml`, profil Maven `installer` (celui qui fabrique l'installeur) :

- `maven-jar-plugin` : `application-dev.properties` est exclu du jar. L'exclusion est faite au
  moment de fabriquer le jar, pas à la copie des ressources : un dossier `target/classes` laissé
  par une compilation précédente ne peut pas le ramener.
- `spring-boot-maven-plugin` : la dépendance `spring-boot-h2console` est exclue du jar final. Le
  pilote H2 lui-même (`h2-*.jar`) reste, c'est la base de données de l'application.

Le code qui crée les données de démo reste dans le programme ; sans son fichier de réglages, il
reste éteint (`liberoshop.demo-data.enabled` absent = désactivé, partie 1).

## Vérifications faites

- **Jar de l'installeur fabriqué** (`mvnw -o -Pinstaller -DskipFrontend=true -DskipTests package`)
  et son contenu listé : `application.properties` présent, `application-dev.properties` absent,
  aucune entrée `h2console`, `h2-2.4.240.jar` présent.
- **Ce jar a été démarré** avec `--spring.profiles.active=dev` sur une base vide : il démarre,
  le profil `dev` est bien actif, et `GET /api/setup/status` répond `required: true` — aucun
  compte de démonstration n'a été créé.
- Au passage, la procédure de secours du point précédent a répondu correctement sur ce serveur
  réel (`RECOVERY_NO_ADMIN` sur une base sans compte).

## Non vérifié

- **L'installeur complet n'a pas été fabriqué** (`mvnw -Pinstaller verify`, avec jpackage et
  WiX) : seul le jar qu'il emballe a été contrôlé.
- Les tests automatiques n'ont pas été relancés pour ce point : seul le profil `installer` du
  `pom.xml` change, et les tests ne l'utilisent pas. La dernière passe complète (524 tests
  serveur) date du point précédent.

## Pour essayer

1. `cd backend` puis `mvnw -Pinstaller -DskipTests package`.
2. `jar tf target/liberoshop-*.jar | findstr /i "application-dev h2console"` : aucune ligne.

## Reste ouvert

- **Le jar fabriqué sans le profil `installer`** contient toujours les deux : ne livrer que ce
  que produit l'installeur.
- **Le code des données de démo** (`bootstrap/`) est toujours dans le programme, inerte. Le
  retirer demanderait de le sortir dans un module à part.
