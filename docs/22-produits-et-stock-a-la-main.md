# Moussa Shop — produits et stock à la main, depuis le menu du produit

Réalisée le 13 octobre 2026.

Demande : « on peut ajouter des produits manuellement, pas besoin d'import ; ajouter du stock,
mais toujours garder l'historique ; un menu sur la carte du produit (ajouter un stock, modifier…) ;
un composant produit réutilisable ; respecter les droits de modification des produits et d'ajout
de stock ; on peut modifier tous les paramètres, comme les unités et les prix d'achat. »

---

## Ce qui change

**Avant.** Un produit n'entrait dans le catalogue que par un fichier d'import. Pour ajouter du
stock il fallait passer par l'écran « Approvisionnement » et choisir un fournisseur — or les
fournisseurs eux-mêmes ne se créent que par import : une boutique sans fichier ne pouvait rien
saisir. Le bouton « Modifier » d'une ligne ne corrigeait que le nom, le prix, le code-barres et
les unités.

**Après.** Sur l'écran **Stock** :

- un bouton **« ＋ Nouveau produit »** crée un produit à la main : nom, prix de vente, unité de
  base, catégorie, code-barres, prix d'achat, stock de départ. Seuls le nom et le prix sont
  obligatoires ;
- chaque produit a un menu **« ⋮ »** :

| Entrée du menu | Ce qu'elle fait |
|---|---|
| **Ajouter du stock** | quantité reçue, unité de réception (pièce, carton, sac…), prix d'achat et fournisseur — les deux derniers sont facultatifs |
| **Modifier le produit** | nom, prix, code-barres, **catégorie**, **prix d'achat**, unité de base et autres unités de vente |
| **Corriger le stock** | casse, périmé, vol, inventaire (rayon compté), avec un commentaire |
| **Historique du stock** | tout ce qui a fait bouger le stock de ce produit : entrées, ventes, corrections |

- les produits s'affichent en **Liste** (tableau) ou en **Cartes** (plus lisible sur téléphone) ;
  le choix est retenu sur l'appareil.

### L'historique est toujours gardé

Le stock ne se modifie jamais directement : il n'existe pas de champ « stock » à corriger dans
la fiche. Chaque quantité passe par un mouvement daté et signé du nom de celui qui l'a saisi :

| Geste | Ce qui est écrit dans l'historique |
|---|---|
| Stock de départ d'un nouveau produit | une **entrée** « Sans fournisseur », au prix d'achat saisi |
| Ajouter du stock | une **entrée**, avec le fournisseur et le prix d'achat s'ils sont donnés |
| Vente | une **sortie**, avec le numéro de facture |
| Corriger le stock | une **correction**, avec son motif et son commentaire |

Une erreur de saisie ne s'efface donc pas : elle se rattrape par une correction, qui apparaît
elle aussi dans l'historique.

### Qui a le droit de quoi

Les droits existants sont repris tels quels ; le menu ne montre à chacun que ce qu'il peut faire,
et le serveur refuse le reste même si on l'appelle directement.

| Action | Responsable de dépôt | Super-admin | Caisse, commandes, agent de dépôt |
|---|---|---|---|
| Créer un produit (avec son stock de départ) | oui | oui | non |
| Modifier un produit, ses unités, son prix d'achat | oui | oui | non |
| **Ajouter du stock** | **oui** | non | non |
| Corriger le stock | oui | oui | non |
| Lire l'historique du stock d'un produit | oui | oui | non |

Le super-admin retrouve le même menu sur **Stock global** (sans « Ajouter du stock »). Un compte
sans aucun de ces droits ne voit pas le bouton « ⋮ ».

### Le prix d'achat

Le prix d'achat affiché dans la fiche est le **coût moyen** du stock, celui qui sert au calcul
des bénéfices. Il continue de se mettre à jour tout seul à chaque entrée de stock qui donne un
prix. La fiche permet maintenant de le **corriger à la main** quand aucune entrée ne l'a donné, ou
l'a donné faux. Vider le champ ne l'efface pas : le coût reste celui qu'il était.

## Détail

Côté serveur :

- `POST /api/products` (nouveau) — crée un produit. Un nom déjà pris est refusé (l'import, lui,
  renomme), un code-barres déjà utilisé aussi. Le stock de départ est enregistré comme une entrée
  sans fournisseur, exactement comme le fait l'import.
- `PUT /api/products/{id}` — accepte en plus la catégorie et le prix d'achat.
- `POST /api/stock/supplies` — le fournisseur devient facultatif. Le droit ne change pas
  (responsable de dépôt seul).
- `GET /api/stock/products/{id}/movements` (nouveau) — l'historique d'un produit, par pages de 30.
- `GET /api/costing/products/{id}` (nouveau) — le coût d'un produit, pour la fiche. Servi à part
  de la fiche elle-même, que tous les rôles lisent : le prix d'achat ne va pas à la caisse.
- Dans les réponses, `fromImport` devient `withoutSupplier` : une entrée sans fournisseur n'est
  plus forcément un import.

Côté écran, des composants réutilisables (`ui/src/app/shared/components/`) :

| Composant | Rôle |
|---|---|
| `app-action-menu` | le bouton « ⋮ » et sa liste d'actions — l'équivalent d'un `mat-menu` (voir « Choix faits ») |
| `app-product-menu` | le menu d'un produit, filtré selon les droits du compte |
| `app-product-card` | la carte d'un produit, avec son menu |
| `app-stock-table` | le tableau existant, avec le même menu sur chaque ligne |
| `app-product-dialogs` | ouvre la fenêtre correspondant à l'action choisie |

Les droits sont dits une seule fois côté écran, dans `core/catalog/product-rights.ts`. Un écran
qui affiche des produits gagne toutes les actions en ajoutant deux lignes : `[manage]="true"`
sur le tableau ou la carte, et `<app-product-dialogs>` à côté.

## Choix faits

- **Pas d'Angular Material.** Le projet n'utilise aucune bibliothèque de composants ; l'ajouter
  pour un seul menu aurait apporté son thème et ses styles à côté de ceux de l'application.
  `app-action-menu` fait la même chose (clic ailleurs, Échap, flèches du clavier, s'ouvre vers le
  haut en bas d'écran) avec les styles existants. À remplacer par un vrai `mat-menu` si vous
  préférez installer Material.
- **Pas de suppression de produit.** Un produit vendu est cité par des factures et des
  mouvements de stock ; le supprimer casserait cet historique. Non demandé, non fait.
- **Le stock de départ est permis au super-admin**, comme l'import (qui ajoute déjà du stock sous
  ce rôle). « Ajouter du stock » sur un produit existant reste réservé au responsable de dépôt.
- **Entrée sans fournisseur permise**, y compris sur l'écran « Approvisionnement » (choix « Sans
  fournisseur »). Dans la liste des entrées, l'étiquette « Import » devient « Sans fournisseur ».
- **Le fichier `product-units-dialog` a été déplacé** de `features/depot-manager` vers
  `shared/components`, pour servir aussi au super-admin.

## Vérifications faites

- **Tests du serveur** : 551 tests, tous verts (`./mvnw -o -DskipFrontend=true test`). Nouveaux :
  `ProductByHandTest` (création, stock de départ dans l'historique, nom ou code-barres déjà pris,
  entrée sans fournisseur et coût moyen, historique mêlant entrées et corrections), deux cas dans
  `ProductEditServiceTest` (catégorie et prix d'achat), deux cas dans `AccessRightsTest` (qui
  peut créer, modifier, ajouter du stock, lire l'historique et le coût — par rôle, avec de vrais
  jetons).
- **Tests de l'interface** : 181 tests, tous verts (`ng test`). Nouveau :
  `product-menu.component.spec.ts` (contenu du menu selon le rôle, aucun bouton pour la caisse,
  fermeture par Échap).
- **Compilation de l'interface** (`ng build`) : sans erreur.
- **Essai dans un vrai navigateur** (Edge, sur un serveur à part avec une base en mémoire et les
  données de démo — pas sur votre base) : le parcours de « Pour essayer » ci-dessous, joué en
  entier par un script. Résultat : produit créé avec 24 pièces ; +10 → 34 ; prix d'achat proposé
  1000 puis fiche à 1058,82 ; catégorie, prix et prix d'achat modifiés ; casse de 2 → 32 ;
  historique à trois lignes ; 4 entrées de menu pour le responsable de dépôt, 3 pour le
  super-admin, aucun bouton pour la caisse ; aucune erreur dans la console. Sur un écran de
  téléphone (360 px), le menu reste dans l'écran et la page ne déborde pas.
  Cet essai a trouvé un défaut, corrigé avant le commit : la fenêtre « Ajouter du stock » ne
  s'ouvrait pas.

## Non vérifié

- **Votre base réelle et votre serveur en marche (port 8080)** : non touchés. Au prochain
  démarrage, la base n'a rien à migrer (aucune colonne nouvelle).
- **L'application Android** : non reconstruite ni essayée ; elle recevra ces écrans avec la
  prochaine version de l'interface.
- **Un produit vendu en plusieurs unités** (sac, carton) dans la fenêtre « Ajouter du stock » :
  le calcul côté serveur est couvert par les tests existants (`SupplyByUnitTest`), mais le choix
  de l'unité dans cette nouvelle fenêtre n'a pas été rejoué dans le navigateur.
- **Navigation au clavier dans le menu** (flèches) : écrite, pas rejouée à la main.

## Pour essayer

1. Se connecter en responsable de dépôt, ouvrir **Stock**.
2. « ＋ Nouveau produit » : nom « Savon test », prix 1500, unité « pièce », prix d'achat 1000,
   stock de départ 24. Le produit apparaît avec 24 pièces.
3. Sur sa ligne, « ⋮ » → **Historique du stock** : une entrée +24, « Sans fournisseur ».
4. « ⋮ » → **Ajouter du stock** : 10, prix d'achat 1200, sans fournisseur. Le stock passe à 34.
5. « ⋮ » → **Modifier le produit** : le prix d'achat affiche 1058,82 (moyenne des deux entrées) ;
   le changer, choisir une catégorie, enregistrer.
6. « ⋮ » → **Corriger le stock** : « Casse », 2. Le stock passe à 32 ; l'historique montre trois
   lignes.
7. Basculer sur **Cartes** : mêmes produits, même menu.
8. Se connecter en super-admin, **Stock global** : le menu n'a pas « Ajouter du stock ».
9. Se connecter en caissier : aucun de ces écrans n'est dans son menu.

## Reste ouvert

- **Créer un fournisseur à la main** : toujours par import seulement. Avec « Sans fournisseur »
  ce n'est plus bloquant, mais un formulaire reste à faire.
- **Qui a changé un prix ou un prix d'achat** n'est pas tracé (point « Piste d'audit » de
  `TODO.md`) : l'historique gardé ici est celui des quantités.
- **Annulation de commande** : les sorties de stock de la commande sont supprimées au lieu d'être
  contre-passées (point déjà dans `TODO.md`) — c'est le seul cas où une ligne disparaît de
  l'historique d'un produit.
- La **carte produit** n'est utilisée que sur l'écran Stock. Les tuiles des écrans de vente sont
  des boutons (un clic ajoute au panier) et ne peuvent pas contenir de menu.
