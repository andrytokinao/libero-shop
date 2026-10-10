# Libero Shop — mots de passe personnels

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Mots de passe ».

---

## Ce qui change

**Avant.**

- Un mot de passe de **6 caractères** suffisait.
- **Personne ne pouvait changer son propre mot de passe** : seul l'administrateur le pouvait,
  pour tout le monde. Il connaissait donc tous les mots de passe, et pouvait vendre ou encaisser
  sous le nom d'un autre sans que rien ne le distingue.

**Après.**

1. **8 caractères au moins** pour tout mot de passe choisi à partir de maintenant. Les mots de
   passe déjà en service continuent de fonctionner : personne n'est bloqué par la mise à jour.
2. **Chacun peut changer le sien** : bouton « Changer mon mot de passe » dans « Mon profil ».
   Il faut taper le mot de passe actuel — un écran resté ouvert ne permet pas à un passant de
   s'approprier le compte.
3. **Un mot de passe donné par l'administrateur est provisoire.** Quand l'administrateur ouvre un
   compte ou redonne un mot de passe, la personne est conduite, à sa connexion suivante, vers la
   page « Mon mot de passe » et **rien d'autre ne s'ouvre tant qu'elle n'a pas choisi le sien**.
   L'administrateur ne le connaît donc plus.

Après un changement, les autres appareils du compte sont déconnectés ; celui qui vient de
changer reste connecté sans rien retaper.

Ne sont pas considérés comme « donnés par l'administrateur » : le compte créé à la première
utilisation, un mot de passe retrouvé par la procédure de secours, et le mot de passe qu'un
administrateur se donne à lui-même. Les comptes qui existaient avant cette mise à jour ne sont
pas invités à changer.

## Détail

Côté serveur :

- `UserAccountService.MIN_PASSWORD_LENGTH` passe de 6 à 8 (création de compte, mot de passe
  redonné, changement personnel).
- `POST /api/auth/password` avec `currentPassword` et `newPassword`, pour tout compte connecté.
  Erreurs : `WRONG_PASSWORD` (l'actuel n'est pas le bon), `SAME_PASSWORD` (le nouveau est
  l'actuel). En cas de succès, toutes les sessions du compte sont fermées, comme pour tout
  changement de mot de passe.
- `UserApp.mustChangePassword` (nouvelle colonne `must_change_password`, à `false` par défaut
  pour les comptes existants) : levé par `createForSomeone` (compte ouvert par l'administrateur)
  et par `setPassword` quand l'administrateur agit sur un autre compte que le sien ; baissé par
  `changeOwnPassword` et par la procédure de secours.
- `GET /api/auth/session` renvoie `mustChangePassword`.

Côté écran :

- `features/auth/change-password.component.ts` (nouveau), page `/mot-de-passe` ;
- `core/guards/auth.guard.ts` : tant que la session dit `mustChangePassword`, toute page de
  l'application renvoie vers `/mot-de-passe` ;
- `AuthService.changePassword` : change, puis se reconnecte aussitôt avec le nouveau mot de passe ;
- « Mon profil » : le bouton « Changer mon mot de passe » ;
- `MIN_PASSWORD_LENGTH` passe à 8 dans les écrans de l'administrateur.

Fichiers : `entity/UserApp.java`, `service/UserAccountService.java`,
`service/AdminRecoveryService.java`, `web/AuthController.java`, `web/UserController.java`,
`web/dto/ChangePasswordRequest.java` (nouveau), `web/dto/SessionResponse.java` ;
`ui/src/app/features/auth/change-password.component.ts` (nouveau), `app.routes.ts`,
`core/guards/auth.guard.ts`, `core/services/auth.service.ts`, `core/models/api.model.ts`,
`shared/components/profile-dialog.component.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 532, tous verts**, dont 2 nouveaux dans `OwnPasswordTest`,
  qui déroule tout le parcours par les vraies adresses :
  - le mot de passe de l'administrateur n'est pas « à changer » ; 7 caractères sont refusés à la
    création d'un compte ; le compte créé doit changer son mot de passe ;
  - mauvais mot de passe actuel, nouveau identique à l'actuel, nouveau trop court : refusés ;
  - après le changement : l'ancienne session est fermée, l'ancien mot de passe ne connecte plus,
    le compte n'est plus invité à changer ;
  - l'administrateur redonne un mot de passe : le compte est de nouveau invité à changer ;
  - sans connexion, pas de changement de mot de passe.
- `UserAccountServiceTest` vérifie en plus qu'un mot de passe posé par l'administrateur lève
  l'invitation.
- **Tests automatiques de l'écran : 163, tous verts.**
- **Interface** : `ng build` passe sans erreur.

## Non vérifié

- **La page « Mon mot de passe » n'a pas été vue dans un navigateur**, ni la redirection à la
  première connexion. La page s'affiche à l'intérieur de l'écran habituel (menu visible) : tant
  que le changement est demandé, un clic dans le menu ramène sur la page. À regarder à l'écran.
- **Mise à jour d'une base existante** : la colonne `must_change_password` est ajoutée au
  démarrage par le même mécanisme que les colonnes précédentes (`ddl-auto=update`, valeur par
  défaut) ; cela n'a pas été essayé sur une copie de base réelle.

## Pour essayer

1. En super-admin, créer un compte avec un mot de passe de 8 caractères.
2. Se connecter avec ce compte : la page « Mon mot de passe » s'affiche ; choisir le sien.
3. « Mon profil » → « Changer mon mot de passe » : le changer encore, en tapant l'actuel.

## Reste ouvert

- **Le changement obligatoire est tenu par l'écran, pas par le serveur.** Une personne qui
  appellerait directement les adresses du serveur avec un mot de passe provisoire ne serait pas
  arrêtée. Le serveur signale l'obligation ; la faire respecter côté serveur demanderait de
  refuser toutes les autres adresses tant qu'elle tient.
- **Pas de limite d'essais sur le mot de passe actuel** dans la page de changement (la connexion,
  elle, est ralentie après 5 échecs). Il faut déjà être connecté pour essayer.
- **Aucune règle de complexité** (chiffres, majuscules) ni liste de mots de passe trop courants :
  seulement la longueur.
- **Application mobile** : un téléphone dont les pages ne sont pas à jour ne connaît pas la page
  de changement ; il fonctionne comme avant jusqu'à sa mise à jour (faite au lancement).
