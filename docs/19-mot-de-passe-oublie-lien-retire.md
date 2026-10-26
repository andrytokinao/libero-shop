# Moussa Shop — mot de passe administrateur oublié : le lien est retiré

Réalisée le 13 octobre 2026.

Demande directe, qui complète
[`16-mot-de-passe-oublie-avec-le-support.md`](16-mot-de-passe-oublie-avec-le-support.md).

---

## Ce qui change

**Avant.** La page de connexion affichait un lien « Mot de passe administrateur oublié ? »,
visible de toute personne assise devant l'ordinateur du serveur.

**Après.**

1. **Le lien n'est plus sur la page de connexion.** Rien dans l'écran n'indique que la procédure
   existe.
2. **La page de récupération existe toujours**, à la même adresse (`/secours`), et fonctionne
   comme avant.
3. **C'est le support qui y conduit** : l'administrateur bloqué appelle le support (le numéro
   WhatsApp reste affiché sur la page de connexion), et le support lui dicte l'adresse, puis
   l'emplacement du code.

## Pour le support : guider l'administrateur

Sur l'ordinateur où Moussa Shop est installé, la page de connexion étant ouverte :

1. Dans la barre d'adresse du navigateur, remplacer la fin `/login` par **`/secours`**, puis
   Entrée. La page « Mot de passe administrateur oublié » s'ouvre.
2. Cliquer « Commencer ».
3. Lire le code : touches **Windows + R**, taper
   `%USERPROFILE%\.libertyshop\security\recovery-code.txt`, Entrée.
4. Taper le code, l'identifiant de l'administrateur et le nouveau mot de passe.

Le code reste valable 15 minutes, une seule fois, et est annulé après cinq mauvais essais.

## Détail

- `ui/src/app/features/auth/login.component.ts` : le lien vers `/secours` est retiré.
- `ui/src/app/features/auth/admin-recovery.component.ts` : commentaire mis à jour, rien d'autre.
- La route `secours` (`ui/src/app/app.routes.ts`) et le serveur ne changent pas.

## Vérifications faites

- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe.
- Le serveur n'est pas touché : ses tests n'ont pas été relancés.

## Non vérifié

- **La page n'a pas été ouverte dans un navigateur** : ni la page de connexion sans le lien, ni
  l'ouverture de `/secours` en tapant l'adresse.

## Reste ouvert

- **C'est toujours une protection par discrétion** : l'adresse `/secours` est la même sur toutes
  les installations. Quelqu'un qui la connaît, ainsi que l'emplacement du code, peut refaire la
  procédure sans appeler. Pour rendre l'appel obligatoire, il faudrait un code fourni par le
  support et vérifié par le logiciel (voir le fichier 16).
- L'application ouverte dans une fenêtre sans barre d'adresse ne permet pas de taper `/secours` :
  il faut alors ouvrir l'adresse dans un navigateur du même ordinateur.
