# Jaugeo — digitalperf

Plateforme de gestion d'un réseau de stations-service (carburants, lubrifiants) : référentiel, exploitation quotidienne, stocks et jaugeage, trésorerie, clients, approvisionnement, contrôle et reporting.

| Dossier | Contenu |
|---|---|
| `jaugeo-api/` | API REST — Java 17, Spring Boot 4.1, Spring Modulith, PostgreSQL 18, Flyway |
| `jaugeo-web/` | Application web — React 19, Vite, Material UI 9, TanStack Query |
| `scripts/` | Environnement de dev, démarrage, vérification de l'API |
| `docker-compose.yml` | PostgreSQL 18 en conteneur (optionnel) |

## État : Sprint 2 livré (journées, index et ventes valorisées)

- **Socle** : monolithe modulaire (14 modules déclarés, frontières vérifiées par test), erreurs RFC 9457 avec code métier, verrouillage optimiste (`VERSION_OBSOLETE`), UUID v7.
- **Sécurité** : jeton d'accès JWT de 15 min gardé en mémoire ; jeton de rafraîchissement de 8 h en cookie HttpOnly, renouvelé à chaque usage. Rejouer un ancien jeton ferme toutes les sessions de l'utilisateur. Le compte est verrouillé 15 min après 5 échecs. Le mot de passe provisoire doit être changé avant tout accès. Il y a 8 rôles et 15 permissions, et le périmètre d'un utilisateur est soit tout le réseau, soit une liste de stations.
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
  - La validation est réservée au superviseur, qui ne peut pas être l'auteur de la clôture. Le renvoi exige un motif, retire les ventes et n'est possible que si la journée suivante n'est pas ouverte.
- **Verrous** : une journée clôturée interdit tout changement de prix ou de compteur daté pendant cette journée. Le trou des prix rétroactifs laissé au Sprint 1 est ainsi fermé.
- **Écrans** : liste des journées par station et ouverture de la suivante ; fiche journée adaptée à la tablette (pistolets regroupés par pompe, aperçu immédiat de la vente, totaux par produit, récapitulatif avant clôture, validation et renvoi).

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
| `JAUGEO_ADMIN_PASSWORD` | `Jaugeo@2026` | à définir pour le premier démarrage |

## Tests

```powershell
cd jaugeo-api; mvn test        # 109 tests : unitaires, architecture modulaire, intégration sur PostgreSQL 18 (Testcontainers)
cd jaugeo-web; npm test        # 36 tests : client API, parcours d'écrans, page Prix, saisie et clôture d'une journée, barèmes, échéances
cd jaugeo-web; npm run lint; npm run build
```

Vérifications sur l'application en cours d'exécution :

```powershell
.\scripts\verifier-api.ps1                                     # 29 contrôles d'API, sur une base neuve
$env:JAUGEO_ADMIN_MDP = "<mdp admin>"; cd jaugeo-web; npm run e2e  # 39 contrôles dans Edge, captures dans e2e/captures
```

`npm run e2e` crée trois utilisateurs de test (`e2e.*`) et un changement de prix aussitôt annulé à chaque exécution. Il ouvre, clôture et valide aussi la journée suivante de Niandane, en partant du 01/01/2026 : à réserver aux bases de développement.

## Conventions

- Code, API et base de données en français : paquet `sn.digitalperf.jaugeo.<module>` et préfixe d'API `/api/v1`.
- Un module = un paquet ; un module n'accède qu'aux classes publiques d'un autre (vérifié par `ArchitectureModulaireTest`).
- Toute évolution du schéma passe par une migration Flyway `V<n>__<description>.sql` ; activer l'audit des nouvelles tables avec `select audit.activer_audit('schema.table')`.
