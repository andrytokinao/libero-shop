# Moussa Shop — serveur derrière un reverse proxy

Réalisée le 10 octobre 2026.

Points de [`TODO.md`](TODO.md) concernés : section 1, « Ralentissement des connexions derrière un
reverse proxy » et « Adresses du réseau local visibles derrière un proxy ».

---

## De quoi il s'agit

Dans l'installation normale, les caisses et les téléphones parlent directement au serveur. Mais
le jour où l'on ajoute devant lui un **relais** (reverse proxy pour le HTTPS, tunnel pour un
accès depuis l'extérieur), toutes les demandes semblent venir du relais : le serveur ne voit plus
qui appelle vraiment. Deux mécanismes s'y trompaient.

## Ce qui change

### 1. Le ralentissement des connexions compte chaque appareil

**Avant.** Après 5 mots de passe faux, le serveur fait attendre l'appareil qui essaie (pas la
caisse d'à côté). Derrière un relais, tous les appareils avaient la même adresse : une personne
qui devinait des mots de passe faisait attendre **tout le monde**, et aucun appareil en
particulier.

**Après.** L'installation déclare l'adresse de son relais (réglage
`liberoshop.security.trusted-proxies`). Pour les demandes qui arrivent de cette adresse — et
seulement celles-là — le serveur lit l'adresse réelle de l'appareil, que le relais indique.

- **Sans relais déclaré (cas normal), rien ne change**, et l'indication n'est même pas lue : un
  appelant ne peut pas se faire passer pour un autre appareil en l'écrivant lui-même.
- Avec un relais déclaré, seule la partie de l'indication **écrite par le relais** est crue ; ce
  que l'appelant a pu y ajouter est ignoré.

### 2. Les adresses du réseau de la boutique restent dans la boutique

**Avant.** La page de téléchargement de l'application mobile liste les adresses du serveur sur
le réseau local (pour les flasher en QR code), mais seulement pour un appelant qui est lui-même
dans la boutique. Derrière un relais installé dans la boutique, **tout visiteur d'Internet
semblait local** et recevait la liste des adresses internes.

**Après.** Le serveur tient compte de l'appelant réel indiqué par le relais : un visiteur venu
d'Internet ne reçoit que l'adresse Internet. Ici aucun réglage n'est nécessaire : se déclarer
« venu d'ailleurs » ne peut que retirer des informations, jamais en donner.

## Détail

- `security/ClientAddress.java` (nouveau) : l'appareil d'où vient une demande. Adresse de la
  connexion, sauf si elle est dans `trusted-proxies` ; dans ce cas, l'en-tête
  `X-Forwarded-For` est lu **de droite à gauche** et la première adresse qui n'est pas elle-même
  un relais déclaré est retenue. En-tête absent ou illisible : l'adresse du relais.
- `web/AuthController.java` : le ralentissement (`LoginThrottle`) utilise `ClientAddress`.
- `security/SecurityProperties.java` et `application.properties` :
  `liberoshop.security.trusted-proxies`, vide par défaut, avec son mode d'emploi.
- `server/ServerController.java` : la liste des adresses locales passe par
  `LocalNetwork.isLocalCaller` (qui juge aussi les adresses relayées) au lieu de la seule adresse
  de la connexion.

Les deux autres endroits qui regardent l'adresse de l'appelant — la création du premier compte et
la procédure de secours — refusaient déjà toute demande relayée ; ils ne changent pas.

Fichiers : `security/ClientAddress.java` (nouveau), `security/SecurityProperties.java`,
`web/AuthController.java`, `server/ServerController.java`, `resources/application.properties` ;
tests `security/ClientAddressTest.java` (nouveau), `server/ServerControllerTest.java`.

## Vérifications faites

- **Tests automatiques du serveur : 537, tous verts**, dont 5 nouveaux :
  - `ClientAddressTest` : sans relais déclaré l'en-tête n'est pas lu ; derrière le relais déclaré
    chaque appareil est compté pour lui-même ; une adresse ajoutée par l'appelant n'est pas crue ;
    deux relais à la suite ; en-tête absent ou illisible ;
  - `ServerControllerTest` : un visiteur d'Internet arrivant par un relais de la boutique ne
    reçoit pas les adresses locales.
- Les tests existants du ralentissement (`LoginThrottleTest`) et de la connexion passent
  toujours.
- Aucun fichier de l'écran n'est modifié.

## Non vérifié

- **Aucun essai derrière un vrai reverse proxy** (nginx, Caddy, tunnel) : le comportement est
  vérifié sur des demandes fabriquées par les tests.
- **L'en-tête `Forwarded`** (la forme normalisée) n'est pas lu pour le ralentissement : seulement
  `X-Forwarded-For`, que les relais courants envoient tous.

## Pour essayer

Sans relais, il n'y a rien à voir : le comportement est celui d'avant. Avec un relais sur la même
machine : régler `liberoshop.security.trusted-proxies=127.0.0.1,::1`, puis taper 6 mots de passe
faux depuis un téléphone : ce téléphone attend, la caisse voisine se connecte normalement.

## Reste ouvert

- **Le réglage est à faire à la main** par celui qui installe le relais ; s'il est oublié, on
  retombe dans le comportement d'avant (tout le monde ralenti ensemble), sans faille nouvelle.
- **HTTPS lui-même** reste le premier point de la section 1 : c'est lui qui amènera un relais
  dans la plupart des installations.
