# Jaugeo — digitalperf

Plateforme de gestion d'un réseau de stations-service (carburants, lubrifiants) : référentiel, exploitation quotidienne, stocks et jaugeage, trésorerie, clients, approvisionnement, contrôle et reporting.

| Dossier | Contenu |
|---|---|
| `jaugeo-api/` | API REST — Java 17, Spring Boot 4.1, Spring Modulith, PostgreSQL 18, Flyway |
| `jaugeo-web/` | Application web — React 19, Vite, Material UI 9, TanStack Query |
| `scripts/` | Environnement de dev, démarrage, vérification de l'API, reprise du classeur |
| `docker-compose.yml` | PostgreSQL 18 en conteneur (optionnel) |
| `deploy/recette/` | Environnement de recette et de production : images Docker, HTTPS (Caddy), sauvegardes ; voir son [README](deploy/recette/README.md) |

## État : Sprint 6 livré (reprise du classeur 2026, saisie hors ligne, tableau de bord du réseau, durcissement)

- **Socle** : monolithe modulaire (14 modules déclarés, frontières vérifiées par test), erreurs RFC 9457 avec code métier, verrouillage optimiste (`VERSION_OBSOLETE`), UUID v7.
- **Sécurité** : jeton d'accès JWT de 15 min gardé en mémoire ; jeton de rafraîchissement de 8 h en cookie HttpOnly, renouvelé à chaque usage. Rejouer un ancien jeton ferme toutes les sessions de l'utilisateur. Le compte est verrouillé 15 min après 5 échecs. Le mot de passe provisoire doit être changé avant tout accès. Il y a 8 rôles et 17 permissions, et le périmètre d'un utilisateur est soit tout le réseau, soit une liste de stations.
- **Audit** : un déclencheur PostgreSQL trace toute écriture, avec son auteur, dans un journal partitionné par mois. Le journal est en insertion seule et ne contient aucun secret.
- **Référentiel** : régions, stations, produits, cuves, pompes et pistolets. Le réseau du classeur *SUIVI DES STATIONS 2026* est chargé : 6 stations et 14 pistolets, avec leurs index au 01/01/2026.
- **Écrans** :
  - connexion et changement de mot de passe imposé ;
  - tableau de bord ;
  - stations : liste, fiche, cuves, pompes et pistolets ;
  - produits ;
  - utilisateurs, avec mot de passe provisoire affiché une seule fois ;
  - journal d'audit, avec le détail avant/après de chaque modification ;
  - mon compte.

Sprint 1 :

- **Prix datés** : chaque changement a une date et une heure d'effet (heure de Dakar), un motif et une référence. Il s'applique à des stations désignées ou à tout le réseau ; les stations qui ne stockent pas le carburant sont écartées.
  - La base interdit deux prix simultanés par une contrainte d'exclusion.
  - Une variation de plus de 20 % doit être confirmée.
  - Un changement programmé s'annule tant qu'il n'est pas entré en vigueur et qu'aucun autre ne le suit.
  - Seule la Direction modifie les prix.
  - L'historique 2026 du classeur est chargé. L'interface `Tarification` (prix en vigueur, découpage d'une période) est prête pour la valorisation des ventes.
- **Barèmes de jaugeage** :
  - le tableau du certificat se colle depuis Excel, en cm ou en mm, avec virgule ou point décimal ;
  - avant l'enregistrement, on contrôle que les volumes croissent, qu'il n'y a pas de doublon et que le barème est cohérent avec la capacité de la cuve ;
  - un calculateur de volume est disponible ;
  - le serveur convertit une hauteur en litres par interpolation linéaire.
- **Historique des équipements** (en insertion seule) :
  - remplacement ou remise à zéro de totalisateur, avec index avant et après contrôlés ;
  - étalonnage, scellés, nettoyage et maintenance ;
  - alerte quand une vérification approche ou est dépassée.
- **Écrans** : page Prix (grille station × produit, historique, changement, annulation), fiche station (barème, événements, historique des prix), carte des prix sur le tableau de bord.

Sprint 2 :

- **Journées d'exploitation** (une par station et par jour, de 00:00 à 24:00 heure de Dakar) : en saisie → clôturée → validée.
  - Ouverture : la première journée d'une station a une date libre ; ensuite, uniquement le jour suivant la dernière journée clôturée, jamais à l'avance.
  - L'index de départ est reporté automatiquement et n'est pas modifiable : arrivée validée de la veille, sinon index de mise en service, ou index du nouveau compteur après un changement de totalisateur.
  - Saisie : index d'arrivée, remises en cuve, et index intermédiaire quand un prix change en cours de journée.
  - Calcul : sortie nette = (arrivée − départ) − remises ; vente = Σ litres × prix de chaque tranche de prix ; montants arrondis au franc.
  - Contrôles bloquants à la clôture : index manquant ou inférieur au départ, débit impossible (plus de 80 L/min sur 24 h, réglable par `jaugeo.exploitation.debit-max-litres-minute`), remise excessive, index intermédiaire manquant ou incohérent, absence de prix.
  - La clôture fige les ventes (table `exploitation.vente`, une ligne par pistolet et par tranche de prix).
  - La validation est réservée au superviseur et à la Direction (Flyway V12), qui ne peuvent pas être l'auteur de la clôture. Le renvoi exige un motif, retire les ventes et n'est possible que si la journée suivante n'est pas ouverte.
- **Verrous** : une journée clôturée interdit tout changement de prix ou de compteur daté pendant cette journée. Le trou des prix rétroactifs laissé au Sprint 1 est ainsi fermé.
- **Écrans** : liste des journées par station et ouverture de la suivante ; fiche journée adaptée à la tablette (pistolets regroupés par pompe, aperçu immédiat de la vente, totaux par produit, récapitulatif avant clôture, validation et renvoi).

Sprint 3 (module `stock`, Flyway V7) :

- **Jaugeages** dans la fiche journée, par cuve : jaugeage de fin obligatoire, et stock de départ uniquement si la veille n'a pas été jaugée (sinon il est reporté). La saisie se fait en litres ou en hauteur (mm), convertie par le barème. Un volume supérieur à la capacité de la cuve est refusé.
- **Stock théorique** = stock physique de la veille + réceptions − sorties nettes des pistolets de la cuve. **Écart** = jaugé − théorique, valorisé au prix en vigueur.
- **Alerte d'écart** jugée sur le cumul du mois (Σ écarts / Σ sorties) :
  - *dans la tolérance* jusqu'à la tolérance du produit (0,5 % Gasoil, 0,6 % Super et Essence pirogue, modifiable sur la fiche produit) ;
  - *alerte* jusqu'à 2 % (`jaugeo.stock.seuil-enquete-pct`) ;
  - *enquête* au-delà.
- **Contrôles bloquants à la clôture** : jaugeage manquant, stock théorique négatif, écart hors tolérance sans observation. La clôture fige les écarts (`stock.ecart_stock`) ; un renvoi les retire.
- **Réceptions (BL)** : fournisseur, transporteur, chauffeur, immatriculation, scellés, quantité BL, jauges avant/après, température, densité. Un même BL n'est réceptionné qu'une fois par fournisseur.
  - Quantité reçue = saisie, sinon jauge après − jauge avant, sinon quantité BL.
  - **Complément citerne** : au-delà d'un manquant de 0,3 % du BL (`jaugeo.stock.tolerance-transport-pct`), tout le manquant est dû par le transporteur et valorisé au prix du jour. La logistique enregistre son règlement. Un complément réglé empêche le renvoi de la journée.
- **Écrans** : section « Stock et jaugeages » de la fiche journée (réceptions, écart en direct, récapitulatif de clôture) ; page Stocks (analyse mensuelle par cuve, compléments citerne) ; carte « Stocks et écarts » du tableau de bord.

Sprint 4 (modules `depenses` et `tresorerie`, Flyway V8) :

- **Dépenses autorisées** payées sur la recette, saisies dans la fiche journée : nature, objet, bénéficiaire, montant, justificatif (photo ou PDF).
  - Chaque nature a un plafond (électricité et eau sans plafond ; gardiennage et entretien 50 000 F ; fournitures 25 000 F ; transport et divers 10 000 F). Au-delà, la dépense attend la décision d'un superviseur ou de la Direction, autre que l'auteur ; un refus exige un motif et laisse la dépense à la charge du gérant.
  - Clôture bloquée tant qu'une dépense attend une décision, ou qu'un justificatif manque si `jaugeo.depenses.justificatif-obligatoire=true`.
- **Montant à verser** = ventes − bons direction − bons gérant − dépenses autorisées − compléments citerne (feuille station, colonne Q). Il est calculé en direct pendant la saisie et figé à la clôture (`tresorerie.recette_journee`, reprise des journées déjà clôturées). Depuis le Sprint 5, les bons et les créances recouvrées en espèces y entrent.
- **Versements bancaires** : date de dépôt, banque, n° de bordereau (BV), montant, photo du BV. Un même BV n'est accepté qu'une fois par banque. La comptabilité vérifie (photo obligatoire, vérificateur autre que l'auteur) ou rejette avec un motif ; un versement rejeté ne compte plus.
- **Solde de caisse du gérant** (solde non versé) reporté jour après jour : solde de la veille + montant à verser − versements du jour. Le livre de caisse mensuel le montre ligne par ligne.
  - **Arrêté de caisse** : *reprise* du solde existant, ou *contrôle* (espèces comptées contre solde théorique, observation obligatoire si écart). Les espèces comptées deviennent le solde ; un écart négatif ouvre un **manquant** au nom du gérant, soldé par remboursement, retenue ou perte. Un arrêté fige le passé : plus de versement ni de renvoi de journée à une date antérieure.
  - **Retard de versement** : solde non versé supérieur aux recettes des 3 derniers jours (`jaugeo.tresorerie.delai-versement-jours`).
- **Pièces jointes** : JPEG, PNG, WebP ou PDF, 5 Mo au plus, type reconnu sur le contenu ; servies avec `nosniff` et une CSP restrictive.
- **Écrans** : sections « Dépenses » et « Recette à verser » de la fiche journée (montant à verser dans le récapitulatif de clôture) ; page Trésorerie (caisse du gérant, versements à vérifier, dépenses à valider, manquants) ; carte « Trésorerie » du tableau de bord.

Sprint 5 (modules `clients` et `reporting`, Flyway V9) :

- **Clients en compte** (gérés par la comptabilité, `CLIENT_GERER`) : code, nom, téléphone, mode de recouvrement, plafond d'encours facultatif, état actif.
  - *Recouvrement par la direction* : les bons sont déduits de la recette et recouvrés par le siège.
  - *Recouvrement par le gérant* : le gérant recouvre lui-même la créance à la station.
- **Bons à crédit** saisis dans la fiche journée (règle 6) : client actif, produit distribué par la station, litres (2 décimales), véhicule ou pirogue, porteur.
  - Le bon est valorisé au prix en vigueur à l'ouverture de la journée (montant arrondi au franc).
  - Un numéro de bon ne sert qu'une fois par station (`BON_DEJA_UTILISE`).
  - Un bon qui porterait l'encours du client (toutes stations) au-delà de son plafond est refusé (`BON_PLAFOND_DEPASSE`, disponible indiqué).
  - Contrôle bloquant à la clôture : les litres servis sur bons d'un produit ne dépassent pas les sorties des pompes.
- **Créances** par client et par station = reprises + bons − règlements.
  - *Reprise* : créance existante au démarrage du suivi, saisie par la comptabilité (par exemple les 24 212 721 F de Cheikh Omar à Hydrobase au 31/12/2025).
  - *Règlement au siège* : virement, chèque, espèces au siège ou compensation.
  - *Recouvrement en espèces à la station* : saisi par le gérant dans la journée, il s'ajoute au montant à verser.
  - Un règlement ne peut pas dépasser la créance de la station (`CREANCE_INSUFFISANTE`).
  - La fiche client montre l'encours, le disponible, la créance par station et le relevé de compte sur une période.
- **Montant à verser** = ventes − bons direction − bons gérant − dépenses autorisées − compléments citerne + créances recouvrées.
- **Synthèse mensuelle des ventes et recettes** (feuille *ANALYSE VENTES & RECETTES*), une ligne par station et une ligne de cumul. Elle est calculée sur les journées clôturées :
  - litres vendus par produit ;
  - E recettes du mois ;
  - F solde de caisse à l'ouverture ;
  - G créances au début du mois ;
  - H = E + F + G ;
  - I versements ;
  - J solde de caisse du gérant ;
  - K créances en fin de mois ;
  - L dépenses autorisées ;
  - M compléments citerne ;
  - O règlements reçus au siège ;
  - Q = I + J + K + L + M + O ;
  - **R = Q − H**.

  Quand toutes les journées sont clôturées, R est égal à la somme des écarts constatés aux arrêtés de caisse.
- **Observations** : chaque ligne porte une observation calculée (journées non clôturées, écart, créances laissées au gérant). Le contrôle de gestion et la comptabilité (`CLOTURE_MENSUELLE`) peuvent y ajouter la leur.
- **Synthèse des stocks** (feuille *ANALYSE DES STOCKS*), par station et par cuve :
  - stock de départ, réceptions et sorties ;
  - stock théorique et stock physique ;
  - écart en litres, en % et en valeur ;
  - niveau d'alerte.

  Un total du réseau par produit complète la feuille.
- **Exports** des deux feuilles :
  - classeur Excel : formules H, Q, R et cumul conservées ;
  - PDF en A4 paysage.
- **Écrans** :
  - section « Ventes à crédit » de la fiche journée : bons, créances recouvrées, montant estimé et alerte de plafond avant l'envoi ;
  - page Clients en compte : liste, fiche, relevé, règlements et reprises ;
  - page Synthèses mensuelles : deux onglets, observations, exports Excel et PDF.
- **Hypothèses à confirmer** :
  - le prix d'un bon est celui de l'ouverture de la journée ;
  - le plafond bloque sans dérogation (`CREDIT_DEROGER` n'est pas encore utilisé) ;
  - les véhicules et pirogues autorisés par client, l'échéance, la facturation et la balance âgée sont reportés au lot 2 ;
  - les créances d'ouverture du classeur sont reprises par l'outil de reprise du Sprint 6.

Sprint 6 :

- **Reprise du classeur *SUIVI DES STATIONS 2026*** (module `reprise`, outil en ligne de commande) :
  - lit les six feuilles station et la feuille d'analyse, puis rejoue chaque journée **par l'API**, avec les mêmes règles qu'à l'écran : ouverture, index, réceptions BL, jaugeages, bons à crédit, dépenses avec justificatif, clôture, validation et versements ;
  - utilise des comptes de reprise à usage unique (gérant, superviseur, comptable), désactivés à la fin ;
  - reprend le solde de caisse d'ouverture par un arrêté REPRISE, et les créances d'ouverture par une opération de reprise. L'analyse de janvier compte le dépôt de la caisse de décembre parmi ses versements : ce dépôt est reconstitué le 1er janvier ;
  - rapproche chaque journée du classeur (litres, ventes, montant dû, écart de stock cumulé du mois), puis chaque mois de la feuille d'analyse ;
  - écrit un rapport Markdown : synthèse, écarts jour par jour, rapprochement mensuel expliqué et particularités du classeur. Les particularités sont les dates en double, les références de bordereau décalées, les index retapés ou en recul, les compléments sans livraison, les jauges absentes ou incompatibles avec les ventes du lendemain, et la colonne CREANCES de Potou ;
  - **résultat sur janvier-février 2026 : 323 journées sur 323 concordent** pour les 6 stations. Les écarts mensuels qui restent viennent de la feuille d'analyse elle-même (arrondis de la colonne R, bons de la direction classés en « Restant BL », caisse de février de Niandane non saisie).

  ```powershell
  .\scripts\reprise-classeur.ps1 -Simulation                    # lecture seule : particularités du classeur
  .\scripts\reprise-classeur.ps1 -Du 2026-01-01 -Au 2026-02-28  # reprise (mot de passe admin demandé)
  ```

  La reprise commence à la première journée à ouvrir de chaque station : elle se fait sur une base sans journée pour ces stations. Code de sortie : 0 si conforme, 2 en cas d'écart ou d'arrêt, 1 en cas d'erreur.
- **Saisie hors ligne de la tablette** :
  - l'application est une PWA : sa coquille est mise en cache par un service worker et elle s'installe sur la tablette. Les réponses de l'API ne sont jamais mises en cache ;
  - pendant une coupure, les index, les jaugeages et la clôture d'une journée sont gardés dans la tablette (IndexedDB), même si elle est éteinte. Ils partent au retour du réseau, dans l'ordre de saisie ;
  - chaque envoi porte un en-tête `Idempotency-Key`. Le serveur mémorise la réponse (table `securite.requete_idempotente`, purgée après 7 jours) : un envoi rejoué après une réponse perdue n'est jamais exécuté deux fois. La même clé utilisée pour une autre requête est refusée (`IDEMPOTENCE_CLE_REUTILISEE`) ;
  - le serveur recalcule tout et refait ses contrôles. Une saisie refusée (journée modifiée entre-temps, clôture impossible…) apparaît comme conflit dans la barre d'application et sur la fiche journée ; les saisies suivantes de la journée attendent qu'elle soit abandonnée ou réessayée ;
  - limite : la session doit avoir été ouverte en ligne. Après un rechargement de la page sans réseau, il faut se reconnecter au retour du réseau ; les saisies gardées partent alors.
- **Tableau de bord du réseau** (page *Réseau*, permission `TABLEAU_BORD_RESEAU`, donnée aussi aux superviseurs par Flyway V13 ; chacun ne voit que les stations de son périmètre) :
  - un niveau par station (normal, alerte, enquête) avec ses motifs : écart de stock du mois au-delà de la tolérance ou du seuil d'enquête, jours sans journée clôturée, journées à valider, manquant de caisse ;
  - volumes de la veille et du mois, ventes, versements, valeur des écarts de stock et caisse du gérant ;
  - carte des stations (fond OpenStreetMap). Les positions chargées par la migration V11 sont celles des localités : à préciser dans la fiche station.
- **Durcissement** :
  - profil `prod` (`SPRING_PROFILES_ACTIVE=prod`) : base, secret JWT et mot de passe initial sans valeur par défaut, Swagger fermé (`JAUGEO_API_DOCS=true` pour l'ouvrir en recette), actuator réduit à `health` ;
  - hors profil `dev`, l'API refuse de démarrer avec le secret JWT de développement (ou trop court ou peu varié), des cookies non sécurisés ou le mot de passe initial par défaut ;
  - en-têtes de sécurité : CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS d'un an en HTTPS, `X-Frame-Options: DENY`.

## Prérequis

- JDK 17 (sur ce poste, `JAVA_HOME` système pointe vers un JDK 8 : utilisez `scripts/env-dev.ps1`)
- Maven 3.9 (ou `mvnw`), Node 24 / npm 11
- PostgreSQL 18 : service Windows local sur le **port 5433**, ou `docker compose up -d` (port 5434)
- Docker Desktop, uniquement pour les tests d'intégration (Testcontainers)

## Base de données

Base `jaugeo`, propriété du rôle `jaugeo` (mot de passe de dev `jaugeo_dev_2026`). Flyway crée les schémas au démarrage de l'API. Pour une installation neuve sur PostgreSQL local, en superutilisateur :

```sql
CREATE ROLE jaugeo LOGIN PASSWORD 'jaugeo_dev_2026';
CREATE DATABASE jaugeo OWNER jaugeo;
\c jaugeo
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
```

## Lancer en développement

```powershell
.\scripts\demarrer-dev.ps1          # ouvre l'API (8080) et le front (5173) dans deux fenêtres
```

ou à la main :

```powershell
. .\scripts\env-dev.ps1             # JDK 17 pour la session
cd jaugeo-api; mvn spring-boot:run
cd jaugeo-web; npm ci; npm run dev  # http://localhost:5173 (relaie /api vers :8080)
```

**Premier accès** : identifiant `admin`, mot de passe initial `Jaugeo@2026` (variable `JAUGEO_ADMIN_PASSWORD`). Ce mot de passe doit être changé dès la première connexion. Le compte n'est créé que si la base ne contient aucun utilisateur.

Documentation de l'API : http://localhost:8080/swagger-ui.html

## Configuration (variables d'environnement)

| Variable | Défaut (dev) | Production |
|---|---|---|
| `JAUGEO_DB_URL` | `jdbc:postgresql://localhost:5433/jaugeo` | obligatoire |
| `JAUGEO_DB_PASSWORD` | `jaugeo_dev_2026` | obligatoire |
| `JAUGEO_JWT_SECRET` | secret de dev | **obligatoire**, ≥ 32 caractères aléatoires |
| `JAUGEO_COOKIE_SECURE` | `false` | `true` (HTTPS) |
| `JAUGEO_ADMIN_PASSWORD` | `Jaugeo@2026` | **obligatoire**, autre que celui de dev |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `JAUGEO_API_DOCS` | Swagger ouvert | `false` (fermé) ; `true` en recette si besoin |

## Tests

```powershell
cd jaugeo-api; mvn test        # 135 tests : unitaires, architecture modulaire, intégration sur PostgreSQL 18 (Testcontainers), dont la reprise d'un classeur synthétique par l'API HTTP
cd jaugeo-web; npm test        # 49 tests : file des saisies hors ligne (coupure, rejeu, conflit), page Réseau, client API, parcours d'écrans, page Prix, saisie et clôture d'une journée avec jaugeage et recette, page Stocks, trésorerie (versement avec photo du BV, vérification), clients (création, fiche, relevé), bon à crédit avec alerte de plafond, synthèse mensuelle (observation, export), barèmes, échéances
cd jaugeo-web; npm run lint; npm run build
```

Vérifications sur l'application en cours d'exécution :

```powershell
.\scripts\verifier-api.ps1                                     # 29 contrôles d'API, sur une base neuve
$env:JAUGEO_ADMIN_MDP = "<mdp admin>"; cd jaugeo-web; npm run e2e  # 43 contrôles dans Edge, captures dans e2e/captures
```

`npm run e2e` utilise trois comptes de test fixes (`e2e.gerant`, `e2e.dg`, `e2e.sup`) : créés à la première exécution, puis leur mot de passe est réinitialisé à chaque exécution. Il crée aussi un changement de prix aussitôt annulé. Il ouvre, jauge (stock de départ 5 000 L par cuve la première fois), clôture et valide aussi la journée suivante de Niandane, en partant du 01/01/2026 : à réserver aux bases de développement.

## Conventions

- Code, API et base de données en français : paquet `sn.digitalperf.jaugeo.<module>` et préfixe d'API `/api/v1`.
- Un module = un paquet ; un module n'accède qu'aux classes publiques d'un autre (vérifié par `ArchitectureModulaireTest`).
- Toute évolution du schéma passe par une migration Flyway `V<n>__<description>.sql` ; activer l'audit des nouvelles tables avec `select audit.activer_audit('schema.table')`.
