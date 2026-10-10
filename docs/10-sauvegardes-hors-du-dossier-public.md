# Libero Shop — sauvegardes hors du dossier public

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Dossier de sauvegarde libre ».

---

## Ce qui change

Le serveur a un dossier **public**, `downloads` : il sert à télécharger l'application Android
sans avoir de compte (on équipe un téléphone avant de lui ouvrir un accès). Tout ce qui s'y
trouvait était téléchargeable par n'importe qui sur le réseau de la boutique.

**Avant.** L'écran « Sauvegardes » acceptait n'importe quel dossier, y compris `downloads`. Un
administrateur qui y aurait rangé ses sauvegardes aurait rendu **toute la base téléchargeable sans
connexion** : ventes, clients, et les mots de passe (sous forme hachée) de tous les comptes.

**Après.** Deux protections, indépendantes l'une de l'autre :

1. **Le dossier public ne sert plus que l'application Android**, sous son nom exact
   (`libero-shop.apk`). Tout autre fichier posé à côté — une sauvegarde, un tableur oublié —
   répond « introuvable », sous-dossiers compris.
2. **L'écran « Sauvegardes » refuse le dossier public** et tout ce qui est dedans, pour
   l'emplacement principal comme pour le second emplacement. Le message dit pourquoi et demande
   un autre dossier.

Le téléchargement de l'application mobile fonctionne comme avant.

## Détail

- `server/DownloadsConfiguration.java` : l'adresse `/downloads/**` ne trouve plus que le fichier
  nommé par `liberoshop.mobile.apk-file`. Les autres noms, et les chemins avec sous-dossier,
  répondent 404 ; un chemin contenant `..` est refusé avant même d'atteindre le dossier (400).
- `backup/BackupService.java` : la validation des réglages refuse un dossier égal au dossier
  public ou situé dessous (code d'erreur `PUBLIC_DIRECTORY`). Le service reçoit la liste des
  dossiers publics à sa construction ; aujourd'hui il n'y en a qu'un.

Fichiers : `server/DownloadsConfiguration.java`, `backup/BackupService.java` ; tests
`server/DownloadsTest.java` (nouveau), `backup/BackupServiceTest.java`.

## Vérifications faites

- **Tests automatiques du serveur : 530, tous verts**, dont 3 nouveaux :
  - `DownloadsTest` : l'APK se télécharge sans connexion ; une sauvegarde posée dans le dossier
    public, à la racine ou dans un sous-dossier, n'est pas téléchargeable, y compris en passant
    par `..` ;
  - `BackupServiceTest` : le dossier public et un de ses sous-dossiers sont refusés comme
    emplacement principal et comme second emplacement ; un dossier à côté est accepté.
- Les tests existants de l'application mobile (`ServerControllerTest`) passent toujours.
- Aucun fichier de l'écran n'est modifié (dernière passe : 163 tests verts).

## Non vérifié

- **Pas d'essai sur l'écran « Sauvegardes »** : le message de refus est celui que l'écran affiche
  pour toute erreur de réglage, il n'a pas été vu à l'écran.
- **Une installation dont les sauvegardes sont déjà réglées dans le dossier public** garde son
  réglage : il n'est pas corrigé tout seul. Ses fichiers ne sont plus téléchargeables (protection
  1), mais le réglage devra être changé à la prochaine modification, qui sera refusée tant que
  le dossier reste celui-là.

## Pour essayer

1. Copier un fichier quelconque dans le dossier `downloads` du serveur, puis ouvrir
   `http://<serveur>/downloads/<ce fichier>` : « introuvable ».
2. `http://<serveur>/downloads/libero-shop.apk` : l'application se télécharge (si elle a été
   publiée).
3. En super-admin, écran « Sauvegardes », saisir le dossier `downloads` : refusé.

## Reste ouvert

- **Les autres dossiers sensibles ne sont pas interdits** : rien n'empêche de choisir le dossier
  de la base elle-même ou un dossier partagé par Windows sur le réseau. Seul ce que le serveur
  Libero Shop publie lui-même est couvert.
- **Les messages d'erreur de sauvegarde contiennent le chemin complet** du dossier : c'est le
  point « Messages d'erreur avec chemins absolus » de la section 1 (super-admin seulement).
