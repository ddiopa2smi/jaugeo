# Jaugeo — digitalperf

Plateforme de gestion d'un réseau de stations-service (carburants, lubrifiants) : référentiel, exploitation quotidienne, stocks et jaugeage, trésorerie, clients, approvisionnement, contrôle et reporting.

| Dossier | Contenu |
|---|---|
| `jaugeo-api/` | API REST — Java 17, Spring Boot 4.1, Spring Modulith, PostgreSQL 18, Flyway |
| `jaugeo-web/` | Application web — React 19, Vite, Material UI 9, TanStack Query |
| `scripts/` | Environnement de dev, démarrage, vérification de l'API |
| `docker-compose.yml` | PostgreSQL 18 en conteneur (optionnel) |

## État : Sprint 0 livré

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
cd jaugeo-api; mvn test        # 70 tests : unitaires, architecture modulaire, intégration sur PostgreSQL 18 (Testcontainers)
cd jaugeo-web; npm test        # 20 tests : client API (rafraîchissement, erreurs), parcours d'écrans
cd jaugeo-web; npm run lint; npm run build
```

Vérifications sur l'application en cours d'exécution :

```powershell
.\scripts\verifier-api.ps1                                     # 29 contrôles d'API, sur une base neuve
$env:JAUGEO_ADMIN_MDP = "<mdp admin>"; cd jaugeo-web; npm run e2e  # 22 contrôles dans Edge, captures dans e2e/captures
```

`npm run e2e` crée un utilisateur de test (`e2e.*`) à chaque exécution : à réserver aux bases de développement.

## Conventions

- Code, API et base de données en français : paquet `sn.digitalperf.jaugeo.<module>` et préfixe d'API `/api/v1`.
- Un module = un paquet ; un module n'accède qu'aux classes publiques d'un autre (vérifié par `ArchitectureModulaireTest`).
- Toute évolution du schéma passe par une migration Flyway `V<n>__<description>.sql` ; activer l'audit des nouvelles tables avec `select audit.activer_audit('schema.table')`.
