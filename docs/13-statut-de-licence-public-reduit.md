# Moussa Shop — statut de licence public réduit

Réalisée le 10 octobre 2026.

Point de [`TODO.md`](TODO.md) concerné : section 1, « Statut de licence public trop bavard ».

---

## Ce qui change

L'adresse `/api/license/status` est volontairement **publique** : elle doit répondre même quand
tout le reste est bloqué, pour que l'application puisse dire « licence expirée, lecture seule ».

**Avant.** Elle donnait à n'importe qui sur le réseau, sans connexion : le nom du client, son
identifiant, le type de licence, la date d'échéance, l'empreinte du poste et l'éventuelle alerte
d'incohérence de l'installation.

**Après.**

- **Sans connexion**, la réponse ne contient plus que l'essentiel : l'état de la licence, si les
  ventes peuvent être enregistrées, la date d'échéance et les jours restants. Le nom du client,
  son identifiant, le type de licence et l'alerte d'incohérence sont absents ; l'empreinte du
  poste est vide ; le message est une phrase neutre (« Connectez-vous pour voir le détail de la
  licence »).
- **Une fois connecté** (tout compte), la réponse est celle d'avant : le bandeau de licence et
  l'écran « Licence » du super-admin fonctionnent sans changement.

## Détail

- `license/LicenseController.java` : `status()` calcule la réponse complète puis, si aucun jeton
  valable n'accompagne la demande, renvoie sa version réduite (`forStrangers()`).
- Côté écran, rien ne dépendait de la version sans connexion : le bandeau de licence n'est
  affiché qu'une fois connecté. Par prudence, la ligne « Empreinte du poste » de la fenêtre de
  détail n'est plus affichée quand l'empreinte est vide
  (`shared/components/license-banner.component.ts`).

Fichiers : `license/LicenseController.java` ; test `web/LicenseStatusPrivacyTest.java`
(nouveau) ; `ui/src/app/shared/components/license-banner.component.ts`,
`core/models/license.model.ts`.

## Vérifications faites

- **Tests automatiques du serveur : 539, tous verts**, dont 2 nouveaux dans
  `LicenseStatusPrivacyTest` :
  - sans connexion : l'état et les jours restants sont là ; pas de nom de client, d'identifiant,
    de type de licence ni d'alerte ; empreinte vide ;
  - connecté (un simple compte de caisse) : l'empreinte est présente.
- **Tests automatiques de l'écran : 163, tous verts** ; `ng build` passe.

## Non vérifié

- Les tests tournent avec la vérification de licence désactivée (cas « aucune licence ») : la
  réduction s'applique de la même façon à une vraie licence, mais ce cas n'a pas son test.

## Reste ouvert — à décider

- **L'empreinte du poste reste lisible sans connexion par deux autres adresses**,
  `/api/license/fingerprint` et `/api/license/renewal-code`. C'est un choix écrit dans le code :
  le client doit pouvoir lire l'empreinte et son code de renouvellement pour commander une
  licence « avant même de pouvoir se connecter ». Je n'y ai pas touché. Comme on peut toujours
  se connecter avec une licence expirée (les comptes ne dépendent pas de la licence), ces deux
  adresses pourraient être réservées au super-admin : **décision à prendre**.
- La date d'échéance reste publique : elle dit la même chose que « jours restants », que le
  point de `TODO.md` demandait de garder.
