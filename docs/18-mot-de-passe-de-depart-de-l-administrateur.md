# Moussa Shop — mot de passe de départ de l'administrateur

Réalisée le 12 octobre 2026.

Demande directe. Remplace l'écran « Première utilisation » de la partie 1
([`REALISATION.md`](REALISATION.md)).

---

## Ce qui change

**Avant.** Une installation neuve n'avait aucun compte. La page de connexion laissait la place à
un écran « Première utilisation » où il fallait inventer un nom, un identifiant et un mot de
passe pour l'administrateur.

**Après.**

1. **L'installation crée elle-même le compte administrateur**, identifiant `admin`, avec un
   **mot de passe de départ tiré au hasard** (10 caractères, différent sur chaque installation).
2. **La page de connexion affiche cet identifiant et ce mot de passe**, dans un encadré
   « Première connexion », sur l'ordinateur où Moussa Shop est installé. L'identifiant est déjà
   rempli ; il reste à recopier le mot de passe.
3. **Après la connexion, rien d'autre ne s'ouvre tant que ce mot de passe n'a pas été remplacé** :
   on arrive sur la page « Mon mot de passe » (le mécanisme existant pour un mot de passe donné
   par quelqu'un d'autre).
4. **Une fois remplacé, l'encadré disparaît pour de bon** et le fichier qui gardait le mot de
   passe de départ est supprimé.

L'écran « Première utilisation » n'existe plus.

Ce qui protège ce mot de passe de départ :

- **Il n'est affiché que sur l'ordinateur du serveur.** Depuis un téléphone ou un autre poste, la
  page dit seulement qu'il est affiché sur cet ordinateur. Sans cela, n'importe qui sur le Wi-Fi
  de la boutique pourrait le lire et se connecter en administrateur avant le gérant.
- **Il est différent sur chaque installation** : pas de mot de passe par défaut que l'on trouve
  dans un manuel.
- Il cesse d'être affiché dès qu'il ne sert plus : remplacé par son titulaire, redonné par un
  autre administrateur, ou retrouvé par la procédure « mot de passe oublié ».

## Détail

Côté serveur :

- `service/InitialSetupService.java` (réécrit) : à la fin du démarrage, s'il n'existe aucun
  compte, crée `admin` (super-admin, « mot de passe à changer ») et écrit le mot de passe dans un
  fichier ; `firstPassword()` le relit tant qu'il correspond encore au compte, sinon supprime le
  fichier.
- `GET /api/setup/status` répond `firstPassword` (vrai tant que le mot de passe de départ est en
  service), et `username` / `password` seulement à l'ordinateur du serveur.
- `POST /api/setup/admin` et `CreateFirstAdminRequest` sont supprimés.
- Emplacement du fichier, réglage `liberoshop.security.first-password-file` :
  `./data/first-password.txt` en développement,
  `~/.libertyshop/security/first-password.txt` pour l'application installée.
- Les comptes de démonstration du profil `dev` existent avant ce contrôle : en développement,
  aucun compte `admin` n'est créé.

Côté écran :

- `features/auth/login.component.ts` : l'encadré « Première connexion » ;
- `features/auth/first-admin.component.ts`, la page `/installation` et son garde sont supprimés ;
- `features/auth/change-password.component.ts` : le texte ne dit plus que le mot de passe vient
  « de l'administrateur » ;
- `core/api/setup.api.ts`, `core/guards/auth.guard.ts`, `app.routes.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 541, tous verts.** Le nouveau `SetupControllerTest` vérifie : mot de passe de 10 caractères affiché au serveur ; ni
  identifiant ni mot de passe depuis un téléphone ou à travers un relais ; connexion en
  super-admin avec obligation de changer ; toujours affiché tant qu'il n'est pas remplacé ; une
  fois remplacé, plus rien d'affiché, fichier supprimé, l'ancien mot de passe ne connecte plus.
- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe. Les tests ont tourné
  avant la retouche d'une phrase dans `change-password.component.ts` ; le build, après.

## Non vérifié

- **La page n'a pas été ouverte dans un navigateur**, ni sur une application installée.

## Pour essayer

Sur une installation neuve (ou en développement avec `liberoshop.demo-data.enabled=false` et une
base vide) : ouvrir la page de connexion sur l'ordinateur du serveur, recopier le mot de passe de
l'encadré, choisir le sien. Se déconnecter : l'encadré ne doit plus être là.

## Reste ouvert

- **Qui s'assoit devant l'ordinateur du serveur avant le gérant voit le mot de passe.** C'était
  déjà vrai de l'écran « Première utilisation » (le premier venu créait le compte). L'installation
  doit donc être terminée par la première connexion, sans laisser le poste en l'état.
- **Le mot de passe de départ est en clair dans un fichier** de l'ordinateur du serveur tant qu'il
  n'a pas été remplacé.
- **Les installations déjà en service ne sont pas concernées** : elles ont déjà un compte.
- L'identifiant `admin` et le nom « Administrateur » se corrigent ensuite dans « Utilisateurs ».
