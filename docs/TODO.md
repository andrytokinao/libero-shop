# Libero Shop — points à améliorer

État au 10 octobre 2026, branche `develop` (commit `cb95375`).

Cette liste vient d'un audit par lecture du code (backend Spring Boot, interface Angular,
enveloppe Android, dépôt git). Les points marqués **(à vérifier)** n'ont pas été confirmés par
un test. Les points marqués **(idée)** ne sont pas des défauts constatés mais des ajouts
possibles.

Priorités : **P1** avant toute installation chez un client · **P2** important · **P3** confort
ou entretien.

---

## Déjà fait

- [x] Modifier un produit après création : nom, prix de base, code-barres (`9c0a5c3`)
- [x] Ralentir les connexions après 5 échecs, par appareil et identifiant (`9c0a5c3`)
- [x] Données de démo : plus insérées quand la configuration ne dit rien (`9c0a5c3`)
- [x] Tests du module licence versionnés (règle `.gitignore` corrigée) (`9c0a5c3`)
- [x] Renommer l'unité de base n'écrase plus le stock d'une vente simultanée (`9c0a5c3`)
- [x] Corrections de stock : casse, perte, inventaire, avec historique (`cb95375`)
- [x] Migration des bases existantes pour accepter le mouvement `ADJUSTMENT` (`cb95375`)
- [x] Données de démo désactivées par défaut, profil `dev` (partie 1, à commiter)
- [x] Clé de signature des sessions conservée entre deux démarrages (partie 1, à commiter)
- [x] Écran « Première utilisation » : création du premier administrateur (partie 1, à commiter)
- [x] Compte relu en base à chaque requête : désactivation et rôles immédiats (partie 2, à commiter)
- [x] Fermeture des sessions : changement de mot de passe et déconnexion (partie 2, à commiter)
- [x] Lecture par rôle : factures, versements, fournisseurs, sorties de stock (partie 2, à commiter)
- [x] Paiements et bordereaux en double refusés (partie 3)
- [x] Un panier envoyé deux fois est une seule vente (partie 3)
- [x] Sorties de stock lues par pages (partie 6, première tranche) (`c9c65d8`)
- [x] Factures lues par période : 30 derniers jours et factures encore ouvertes, ou dates choisies (`4fa4a54`)
- [x] Mot de passe administrateur perdu : procédure de secours sur l'ordinateur du serveur (`b6d27b2`)
- [x] Jar de production sans réglages de démo ni console H2 (`ab7a89a`)
- [x] Notifications en direct selon les droits, connexions fermées quand l'accès change (`0e2805b`)
- [x] Le menu suit un changement de rôle sans recharger la page (`64014c6`)
- [x] Sauvegardes hors du dossier public, qui ne sert plus que l'application Android

Le détail de chaque partie est dans [`REALISATION.md`](REALISATION.md) (parties 1 et 2), puis
dans un fichier par partie : [`REALISATION-partie-3-argent.md`](REALISATION-partie-3-argent.md),
[`04-sorties-de-stock-par-pages.md`](04-sorties-de-stock-par-pages.md),
[`05-factures-par-periode.md`](05-factures-par-periode.md),
[`06-mot-de-passe-administrateur-perdu.md`](06-mot-de-passe-administrateur-perdu.md),
[`07-jar-de-production-sans-outils-de-developpement.md`](07-jar-de-production-sans-outils-de-developpement.md),
[`08-notifications-en-direct-selon-les-droits.md`](08-notifications-en-direct-selon-les-droits.md),
[`09-menu-apres-un-changement-de-role.md`](09-menu-apres-un-changement-de-role.md),
[`10-sauvegardes-hors-du-dossier-public.md`](10-sauvegardes-hors-du-dossier-public.md).

---

## 1. Sécurité et configuration

- [ ] **P1 — Tout circule en HTTP clair.** Mots de passe et jetons passent en clair sur le Wi-Fi
  de la boutique, que les clients utilisent aussi pour commander par QR.
  *Piste :* TLS sur le serveur (certificat auto-signé ou reverse proxy), puis
  `androidScheme: 'https'` dans `ui/capacitor.config.ts` ; à défaut, un Wi-Fi invité séparé.
- [x] **P1 — Données de démo actives dans le fichier livré.** Désactivées par défaut, réservées
  au profil `dev` ; une installation neuve demande de créer le premier administrateur.
  *Fait — voir `REALISATION.md`, partie 1.*
- [x] **P1 — Secret JWT vide.** Chaque installation génère sa clé au premier démarrage et la
  conserve dans un fichier ; `LIBEROSHOP_JWT_SECRET` reste possible.
  *Fait — voir `REALISATION.md`, partie 1.*
- [x] **P2 — Mot de passe administrateur perdu.** Procédure de secours sur l'ordinateur du
  serveur seulement : un code écrit dans un fichier de cet ordinateur, à retaper.
  *Fait — voir `06-mot-de-passe-administrateur-perdu.md`.*
- [x] **P3 — `application-dev.properties` embarqué dans le jar.** Exclu du jar de l'installeur
  (profil Maven `installer`). *Fait — voir `07-jar-de-production-sans-outils-de-developpement.md`.*
- [x] **P1 — Un admin désactivé ou rétrogradé garde ses droits 12 h.** Le compte (actif + rôles)
  est relu en base à chaque requête.
  *Fait — voir `REALISATION.md`, partie 2.*
- [x] **P1 — Aucune révocation de jeton.** Un changement de mot de passe ferme toutes les
  sessions du compte, la déconnexion ferme celle de l'appareil.
  *Fait — voir `REALISATION.md`, partie 2.*
- [x] **P1 — Tout rôle connecté lit tout.** Factures, versements, fournisseurs et sorties de
  stock sont limités selon le rôle.
  *Fait — voir `REALISATION.md`, partie 2.*
- [x] **P2 — Les notifications en direct ignorent les rôles.** Chaque compte connecté ne reçoit
  que les commandes qu'il peut lire, et ses connexions sont fermées quand son accès change
  (désactivation, rôles, mot de passe, déconnexion).
  *Fait — voir `08-notifications-en-direct-selon-les-droits.md`.*
- [x] **P3 — Menu après un changement de rôle.** L'écran ouvert relit sa session quand le serveur
  ferme sa connexion en direct : nouveau menu, accueil du nouveau rôle, ou page de connexion.
  *Fait — voir `09-menu-apres-un-changement-de-role.md`.*
- [x] **P2 — Dossier de sauvegarde libre.** Le dossier public `downloads` ne sert plus que le
  fichier APK, et l'écran « Sauvegardes » refuse ce dossier et ses sous-dossiers.
  *Fait — voir `10-sauvegardes-hors-du-dossier-public.md`.*
- [ ] **P2 — Mots de passe.** 6 caractères minimum, et un utilisateur ne peut pas changer le
  sien : l'admin les connaît tous.
  *Piste :* écran « changer mon mot de passe », minimum 8 caractères, changement forcé à la
  première connexion.
- [ ] **P2 — Ralentissement des connexions derrière un reverse proxy.** Le comptage utilise
  l'adresse vue par le serveur ; derrière un proxy, tous les appareils auraient la même.
  *Piste :* lire `X-Forwarded-For` quand un proxy de confiance est configuré.
- [ ] **P3 — Statut de licence public trop bavard.** `/api/license/status` renvoie nom du client,
  plan, échéance et empreinte machine sans connexion. Ne renvoyer que l'état et les jours restants.
- [x] **P3 — Console H2 livrée dans le jar.** Retirée du jar de l'installeur.
  *Fait — voir `07-jar-de-production-sans-outils-de-developpement.md`.*
- [ ] **P3 — Adresses du réseau local visibles derrière un proxy** (`server/ServerController.java`).
- [ ] **P3 — Messages d'erreur avec chemins absolus** (sauvegarde, licence ; super-admin seulement).

## 2. Licence

- [ ] **P2 — Essai réinitialisable.** Une base neuve, trois fichiers et une clé de registre
  supprimés redonnent 40 jours. Seule une activation en ligne le ferme.
- [ ] **P2 — Garde anti-recul d'horloge fragile.** État dans un seul fichier, échecs d'écriture
  silencieux, chemin non verrouillé : une horloge figée avant l'échéance n'expire jamais.
  *Piste :* dupliquer la date haute en base et dans le registre.
- [ ] **P2 — Contournement possible du garde de propriétés (à vérifier).** Un dossier ajouté au
  classpath avec `liberoshop.license.enabled=false` pourrait être pris pour le fichier livré.
  *Piste :* compiler les interrupteurs dans le build de production.
- [ ] **P3 — Documentation périmée.** `LICENSE-MODULE.md` cite un `tools/license-generator`
  supprimé, et des commentaires parlent encore de « 30 jours ».

## 3. Argent et intégrité des données

- [x] **P1 — Paiements et remises en double.** Compteur de modifications sur `Invoice` et
  `CashRemittance` : le second arrivé est refusé (409) sans rien écrire.
  *Fait — voir `REALISATION-partie-3-argent.md`.*
- [x] **P1 — Vente en double si le réseau coupe à l'encaissement.** La caisse nomme chaque
  panier ; renvoyé, il reçoit la facture déjà faite.
  *Fait — voir `REALISATION-partie-3-argent.md`.*
- [ ] **P2 — Commande par QR code envoyée deux fois.** Les commandes passées depuis une table
  n'envoient pas de référence de panier : une coupure peut encore en créer deux.
- [ ] **P2 — Le rapport journalier mélange deux dates.** Total par date de vente, répartition par
  date de paiement (`DashboardService`) : un crédit d'hier payé aujourd'hui fausse le
  rapprochement de caisse.
- [ ] **P2 — Piste d'audit.** Ni `createdAt`/`updatedBy`, ni journal ; aucune trace de qui a
  changé un prix ou une unité.
- [ ] **P2 — Annulation de commande.** Les sorties de stock sont supprimées au lieu d'être
  contre-passées (`InvoiceService`).
- [ ] **P3 — Montants non bornés.** Une quantité ou un prix trop grand renvoie une erreur 500 ;
  l'import accepte un prix à 0.
- [ ] **P3 — Numéros de facture.** Uniques, mais avec des trous quand une vente échoue.
- [ ] **P3 — Fuseau horaire.** « Aujourd'hui » dépend de l'horloge et du fuseau du PC serveur, et
  côté écran de celle du téléphone.

## 4. Fonctions métier manquantes

- [ ] **P1 — Retours et remboursements.** Une vente payée ne s'annule pas.
  **Décision attendue :** l'argent rendu sort-il de la caisse du jour (et réduit la remise du
  caissier), ou est-il suivi à part ?
- [ ] **P2 — Retirer un produit de la vente** (désactivation, sans perdre l'historique).
- [ ] **P2 — Remises** par ligne ou sur le total.
- [ ] **P2 — Seuil de stock bas par produit** (aujourd'hui 10 pour tout le catalogue,
  `StockPolicy`).
- [ ] **P3 — Corrections de stock dans une autre unité** (« 2 cartons ») : aujourd'hui en unité de
  base seulement.
- [ ] **P3 — Corrections de stock : choix du produit par recherche** au lieu d'une liste
  déroulante complète (même limite sur l'écran d'approvisionnement).
- [ ] **P3 — Corrections de stock dans le menu du super-admin** (autorisé côté serveur, absent de
  son menu).
- [ ] **P3 — Inventaire complet (idée).** Saisir le comptage de tout un rayon en une fois, avec
  récapitulatif des écarts avant validation.
- [ ] **P3 — Pertes dans les rapports (idée).** Faire apparaître casse, vol et périmés dans
  « Bénéfices & stock ».
- [ ] **P3 — Dates de péremption et lots (idée).**
- [ ] **P3 — Fiche client et suivi des crédits (idée).** Solde dû par client, historique,
  plafond de crédit.
- [ ] **P3 — Exports (idée).** Ventes, stock et caisse en Excel ou PDF pour le comptable.
- [ ] **P3 — Génération du PDF de facture.** `Invoice.generatePdf()` existe mais n'est pas
  implémentée.

## 5. Performance

- [x] **P2 — Sorties de stock chargées en entier.** Lues par pages de 50, recherche et totaux
  faits par la base. *Fait — voir `04-sorties-de-stock-par-pages.md`.*
- [x] **P2 — Factures chargées en entier.** Lues par période : sans dates, les 30 derniers jours
  et tout ce qui est encore ouvert ; sinon les dates choisies à l'écran.
  *Fait — voir `05-factures-par-periode.md`.*
- [ ] **P3 — Approvisionnements, corrections et produits chargés en entier** (`StockService`,
  `CatalogService`) : listes qui grossissent lentement.
- [ ] **P2 — Statistiques calculées en mémoire.** `findAll()` deux fois par tableau de bord,
  listes chargées pour un simple `.size()`.
- [ ] **P2 — Requêtes en cascade (N+1)** sur les remises et l'activité des utilisateurs.
- [ ] **P3 — Index manquants** sur les colonnes de date (`sale_date`, `invoice_date`,
  `payment_date`, `movement_date`).
- [ ] **P3 — Recherche sans temporisation** sur « Toutes les factures » : une requête par frappe.

## 6. Base de données et déploiement

- [ ] **P2 — Schéma en `ddl-auto=update`.** Les contraintes et colonnes existantes ne sont jamais
  mises à jour ; chaque évolution demande une migration écrite à la main (cinq à ce jour).
  *Piste :* Flyway.
- [ ] **P2 — MySQL/PostgreSQL non testés.** Les migrations utilisent la syntaxe H2 et la
  sauvegarde ne fonctionne que sur H2. Retirer les deux pilotes ou les tester.
- [ ] **P2 — Les tests n'utilisent pas la configuration de production (à vérifier).** H2 en
  mémoire, jamais le fichier ni `ddl-auto=update`.
- [ ] **P3 — Version `0.0.1-SNAPSHOT`** et métadonnées vides dans `pom.xml`.
- [ ] **P3 — Sauvegarde hors du poste (idée).** Copie automatique sur clé USB ou stockage
  distant, avec alerte si la dernière sauvegarde est ancienne.

## 7. Interface et caisse

- [ ] **P2 — Le panier en cours n'existe qu'en mémoire.** Un rechargement, un clic de menu ou une
  session expirée le perd (`new-sale.component.ts`).
- [ ] **P2 — Une coupure réseau au démarrage déconnecte l'utilisateur** (`auth.service.ts` efface
  le jeton sur toute erreur, pas seulement 401/403).
- [ ] **P2 — Session qui expire sans prévenir** en plein service : ni jeton de rafraîchissement,
  ni alerte.
- [ ] **P2 — Pas d'état de chargement ni d'erreur** sur la plupart des écrans : après un échec on
  lit « Aucune facture… ».
- [ ] **P2 — Aucun mode hors ligne** à la caisse.
- [ ] **P3 — Écran de licence** : installation et renouvellement sans protection contre le double
  clic.
- [ ] **P3 — Textes en français codés en dur**, souvent sans accents côté serveur ; « Ar » codé
  en dur.
- [ ] **P3 — Fenêtre « Produit et unités » à vérifier dans un navigateur** : les nouveaux champs
  (nom, prix, code-barres) et l'écran des corrections ont été compilés, pas essayés à l'écran.
- [ ] **P3 — Gros fichiers à découper** : `product-import-dialog` (873 lignes), `backups`
  (822), `new-sale` (726), `supply` (656), `styles.scss` (2029), `ProductImportService` (868).
- [ ] **P3 — Contraste des couleurs (à vérifier).**

## 8. Application Android

- [ ] **P2 — Mises à jour téléchargées en HTTP sans signature** : un appareil du réseau local
  peut pousser du JavaScript sur tous les téléphones.
- [ ] **P2 — `allowBackup="true"`** : le jeton part dans les sauvegardes du téléphone.
- [ ] **P3 — `minifyEnabled false`** et `versionCode` incrémenté à la main.
- [ ] **P3 — Sauvegarde du keystore (à vérifier).** Le perdre oblige à réinstaller l'application
  sur tous les téléphones.
- [ ] **P3 — Scanner dépendant de Google Play Services (à vérifier)** sur les téléphones sans
  Play Services ou sans Internet.
- [ ] **P3 — Paquets de test du modèle Capacitor** (`com.getcapacitor.myapp`) à supprimer.

## 9. Tests et outillage

- [ ] **P2 — Angular 18 n'est plus maintenu.** Planifier `ng update`.
- [ ] **P2 — Ni lint, ni formateur, ni CI.** Rien ne lance les tests automatiquement.
- [ ] **P2 — Tests manquants côté serveur** : concurrence, contrôleurs et rôles, rapports du
  tableau de bord, gestion des erreurs.
- [ ] **P2 — Tests manquants côté écran** : authentification, intercepteurs, gardes, écran de
  vente et d'encaissement ; aucun test de bout en bout.
- [ ] **P3 — Erreurs non prévues** (`ApiExceptionHandler`) : format Spring par défaut au lieu du
  format de l'application.
- [ ] **P3 — Code mort** : `SaleRepository.findBySellerIdOrderBySaleDateDesc` (à vérifier).

## 10. Dépôt git

- [ ] **P3 — `backend/libero-shop.png`** : marqué « ajouté » dans git mais supprimé du disque.
  À retirer de l'index ou à restaurer.
- [ ] **P3 — Installation accidentelle à la racine** : `package.json`, `package-lock.json` et
  `node_modules/claude`.
- [ ] **P3 — Restes** : `front.html` (ancienne maquette), captures de `scratch/screens`,
  `ui/src/app/release/`.
- [ ] **P3 — Pas de README à la racine** ; celui de `ui` est le modèle Angular.
- [ ] **P3 — `backend/.gitignore` ignore `/.mvn/`** alors que `mvnw` en a besoin (à vérifier sur
  un clone neuf).

---

## Ordre conseillé

1. ~~Configuration de production : données de démo et secret JWT (section 1).~~ Fait.
2. ~~Droits : rôles relus en base, révocation, lecture par rôle (section 1).~~ Fait.
3. ~~Argent : doubles paiements et vente en double (section 3).~~ Fait.
4. Retours clients, une fois la décision prise (section 4).
5. HTTPS ou Wi-Fi séparé — décision d'installation plus que de code (section 1).
6. Pagination et Flyway avant que les bases ne grossissent (sections 5 et 6).
