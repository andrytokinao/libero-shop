# Libero Shop — accès par Internet : le support est indiqué

Réalisée le 11 octobre 2026.

Demande directe, hors `TODO.md`.

---

## Ce qui change

Sur la page « Application mobile », le choix **« Partout (Internet) »** sert aux téléphones qui ne
sont pas sur le Wi-Fi de la boutique (4G, maison, autre dépôt). Il ne fonctionne que si le serveur
a été ouvert sur Internet.

**Avant.** Quand ce n'était pas le cas, la page disait seulement que le serveur n'était pas
accessible depuis Internet. Un administrateur connecté voyait en plus une consigne technique
(redirection de port, reverse proxy, réglage `liberoshop.mobile.public-url`), inutilisable pour un
commerçant.

**Après.** La page indique à tout le monde **qui appeler** : le support de Libero Shop, avec son
numéro WhatsApp, qui se charge de rendre le serveur accessible depuis Internet. La consigne
technique a été retirée de l'écran ; elle reste dans `mobile-android.md` pour celui qui fait
l'installation.

Quand le serveur est déjà ouvert sur Internet, rien ne change : le QR code s'affiche comme avant.

## Détail

- `ui/src/app/features/mobile/mobile-download.component.ts` : le message du cas « Internet non
  disponible », avec le numéro du support (`SUPPORT`, dans `core/config/publisher.ts`).

## Vérifications faites

- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe.

## Non vérifié

- **La page n'a pas été ouverte dans un navigateur.**

## Reste ouvert

- Ouvrir un serveur sur Internet reste une intervention à la main du support (box, nom de
  domaine, HTTPS) : voir le point « HTTPS ou Wi-Fi séparé » de `TODO.md`.
