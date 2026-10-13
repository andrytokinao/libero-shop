# Libero Shop — les produits en liste à la vente, au choix avec les tuiles

Réalisée le 13 octobre 2026.

Demande : « si tu trouves une nouvelle expérience utilisateur pour saisir une vente ou rechercher
un produit, génère-m'en une autre : conserver l'existant, mais créer un autre composant pour la
liste de produits, et ajouter un mode d'affichage au choix. »

---

## Ce qui change

**Avant.** À la caisse (« Nouvelle vente ») et à la prise de commande, les produits ne
s'affichaient que d'une façon : des tuiles à toucher.

**Après.** Un bouton à deux positions, à côté du champ de recherche, laisse choisir :

| Affichage | Pour qui |
|---|---|
| **Tuiles** (inchangé, toujours l'affichage de départ) | l'écran tactile, un catalogue court, des vendeurs qui reconnaissent les produits à leur place |
| **Liste** (nouveau) | un grand catalogue, une caisse au clavier, des noms longs qu'on veut lire en entier |

Le choix s'applique tout de suite, vaut pour les deux écrans de vente, et **reste mémorisé sur
l'appareil** : la caisse peut rester en liste pendant que le téléphone d'un serveur garde ses
tuiles. Rien n'est enregistré sur le serveur.

## La vue Liste

Une ligne par produit :

- le nom en entier, et dessous son rayon et ce qui reste en stock (en rouge quand le stock est
  bas, « Épuisé » quand il n'y en a plus) ;
- le prix dans une colonne, toujours au même endroit ;
- au bout de la ligne, **« + »** pour ajouter, puis **« − 2 + »** dès qu'il y en a dans le
  panier : on corrige la quantité sans aller dans le panier ;
- l'étoile des favoris au début de la ligne ;
- la ligne se colore quand le produit est dans le panier, et passe en rouge si le stock ne suffit
  plus.

Les rayons sont de petits boutons au-dessus des lignes (« Épicerie 6 »), avec « ★ Favoris » en
premier. Une fois dans un rayon, le chemin « ‹ Tous › Épicerie » est le même qu'avec les tuiles.

Un produit vendu en plusieurs unités (kapoka, kg, sac) garde une seule ligne ; la toucher ouvre
ses boutons d'unités sous la ligne, comme sous une tuile.

À l'écran, la liste montre environ **trois fois plus de produits** que les tuiles sur un écran
de caisse, et un nom n'y est jamais coupé.

## Vendre au clavier, sans la souris

C'est la nouveauté pour la recherche. Dans la vue Liste, à la caisse, le curseur reste dans le
champ de recherche et :

1. on tape quelques lettres — `riz` ;
2. **↓** et **↑** déplacent une surbrillance sur les lignes trouvées ;
3. **Entrée** ajoute la ligne en surbrillance ; Entrée encore en ajoute une de plus.

`3*riz`, ↓, Entrée en ajoute trois d'un coup (la même écriture que pour un code scanné). Si le
stock ne suffit pas, un message dit combien ont été ajoutés.

**Le lecteur de codes-barres n'est pas gêné** : dès que le texte du champ change, la surbrillance
disparaît, et Entrée redevient celle de la caisse (le code scanné ou tapé). Un scan ne peut donc
pas ajouter par erreur la ligne restée en surbrillance.

Les touches F2, F4, F8, F9, F10 ne changent pas. Avec les tuiles, ↑ et ↓ ne font rien de
nouveau.

## Fichiers

Nouveaux (dans `ui/src/app`) :

- `shared/components/catalog-list.component.ts` — la liste. Mêmes entrées et sorties que
  `catalog-grid.component.ts`, qui n'est pas modifié : un écran remplace l'un par l'autre.
- `shared/components/catalog-view-toggle.component.ts` — le bouton Tuiles / Liste.
- `core/services/catalog-view.service.ts` — le choix, gardé dans le navigateur
  (`liberoshop.catalogView`), sur le modèle du thème clair / sombre.

Modifiés :

- `features/cashier/new-sale.component.ts` — le bouton, la liste, et les flèches / Entrée du
  champ de recherche.
- `features/orders/order-taking.component.ts` — le bouton et la liste (sans les flèches : c'est
  un écran de téléphone).
- `core/ui/icons.ts` — une icône « tuiles ».

Rien ne change côté serveur.

## Vérifications faites

- **Tests de l'interface : 176 réussis** (`ng test`), dont 12 nouveaux : le choix d'affichage
  (départ en tuiles, relu au démarrage, mémorisé) et la liste (lignes et rayons, ajout et
  retrait sur la ligne, produit à plusieurs unités, flèches arrêtées aux deux bouts, Entrée
  limitée par le stock, surbrillance oubliée quand le texte change ou que la ligne sort de la
  liste).
- **`ng build`** : réussi.
- **Essai dans un navigateur** (Edge piloté, serveur de démonstration lancé à part sur une base
  jetable — votre base n'a pas été touchée) :
  - caisse en 1366 × 850 : passage Tuiles → Liste, 12 lignes et 4 rayons affichés, choix relu
    après reconnexion ;
  - `ri`, ↓ ↓ ↑, Entrée deux fois : 2 « Café moulu » au panier, total juste ;
  - `3*su`, ↓, Entrée : 3 « Sucre » ;
  - nouveau texte tapé : plus de surbrillance ;
  - « + » sur une ligne : panier et total à jour ;
  - téléphone (390 × 800) : caisse et prise de commande en liste, sans défilement horizontal ;
    retour aux tuiles sur la prise de commande ;
  - aucune erreur dans la console du navigateur.

## Non vérifié

- **Un produit à plusieurs unités dans la vue Liste, à l'écran** : la base de démonstration n'en
  contient pas. Couvert par un test automatique (la ligne ouvre ses boutons d'unités) seulement.
- **Un vrai lecteur de codes-barres** : le comportement (la surbrillance s'efface dès que le
  texte change) est testé, pas essayé avec un lecteur.
- **Un vrai téléphone et l'application Android** : essai fait dans un navigateur à la taille d'un
  téléphone.
- Pendant la mise en place de l'essai, **une tentative de connexion avec le compte de
  démonstration `fatima` a été faite sur votre serveur en marche (port 8080)** ; elle a été
  refusée (mot de passe incorrect) et rien d'autre n'y a été fait.

## Pour essayer

1. Caisse → « Nouvelle vente » : à droite du champ de recherche, cliquer sur l'icône à trois
   lignes (« Liste »).
2. Taper `riz`, puis ↓ et Entrée : le riz est au panier. Entrée encore : deux.
3. Sur une ligne, « − » et « + » changent la quantité.
4. Scanner un article : il s'ajoute comme avant.
5. Cliquer sur l'icône à quatre carrés : les tuiles reviennent.
6. Sur un téléphone, prise de commande : le même bouton, sous les champs.

## Reste ouvert

- **Sur le téléphone, à la prise de commande, le bouton d'affichage prend une ligne à lui** sous
  les champs « Client » et « Produit » (faute de place à côté). À déplacer si cela gêne.
- **Dans la vue Liste, l'étoile d'un rayon** ne se pose qu'une fois entré dans le rayon (dans le
  chemin), pas sur son bouton.
- **La page de commande du client (QR code de table)** garde ses tuiles : pas de choix
  d'affichage pour le client.
- Les flèches ne parcourent que les produits, pas les rayons.
