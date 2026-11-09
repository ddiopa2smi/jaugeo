# Environnement de recette Jaugeo

Ce dossier installe Jaugeo sur un serveur Linux avec Docker : la même pile servira à la production, avec un autre domaine et d'autres secrets.

| Service | Rôle | Exposé |
|---|---|---|
| `caddy` | HTTPS : certificat Let's Encrypt du domaine, renouvelé automatiquement ; redirection de HTTP vers HTTPS | 80, 443 |
| `web` | Nginx : application React (PWA), en-têtes de sécurité, relais de `/api` vers l'API | non |
| `api` | API Spring Boot, profil `prod` | non |
| `postgres` | PostgreSQL 18 (volume `donnees`) | non |
| `sauvegarde` | `pg_dump` au démarrage puis chaque jour, avec rotation | non |

Les pièces jointes (photos de BV, justificatifs) sont dans la base : la sauvegarde de la base suffit.

## Prérequis

- Serveur virtuel Linux : 2 vCPU, 4 Go de RAM et 40 Go de disque suffisent pour le lot 1.
- Docker Engine 24 ou plus, avec le plugin `docker compose`.
- Un enregistrement DNS A du domaine (par exemple `recette.jaugeo.sn`) vers l'adresse du serveur.
- Les ports 80 et 443 ouverts. Le port 80 sert à obtenir le certificat.

## Installation

```sh
git clone <dépôt> jaugeo && cd jaugeo/deploy/recette
cp .env.exemple .env && chmod 600 .env
# Renseigner .env : un secret différent pour chaque variable, par exemple avec openssl rand -base64 48
docker compose up -d --build
docker compose ps          # api, web et postgres « healthy »
```

Ouvrez `https://<domaine>` et connectez-vous en `admin` avec `JAUGEO_ADMIN_PASSWORD`. Le mot de passe doit être changé à la première connexion. Une fois ce changement fait, retirez `JAUGEO_ADMIN_PASSWORD` de `.env` ou remplacez-le par une valeur quelconque : il ne sert que si la base n'a aucun utilisateur.

L'API refuse de démarrer si un secret manque, si le secret JWT est trop faible ou si le mot de passe initial est celui de développement (voir `docker compose logs api`).

## Charger les données réelles (reprise du classeur)

Pour que la recette porte sur des données réelles, rejouez le classeur sur la base de recette depuis un poste qui a le classeur :

```powershell
.\scripts\reprise-classeur.ps1 -Simulation                                              # particularités du classeur
.\scripts\reprise-classeur.ps1 -Du 2026-01-01 -Au 2026-02-28 -Api https://recette.jaugeo.sn
```

La reprise demande une base qui n'a encore aucune journée pour les stations reprises. Faites-la juste après l'installation, avant d'ouvrir la recette aux utilisateurs.

## Mise à jour

```sh
git pull
docker compose up -d --build     # Flyway applique les nouvelles migrations au démarrage de l'API
```

Une sauvegarde est faite à chaque redémarrage du service `sauvegarde`. Avant une mise à jour qui contient des migrations, lancez `docker compose restart sauvegarde`.

Pour figer une version livrée, mettez son étiquette Git dans `JAUGEO_VERSION` (par exemple `JAUGEO_VERSION=v1.0.0-rc1`).

## Sauvegardes et restauration

Les fichiers `jaugeo-AAAAMMJJ-HHMMSS.dump` sont dans `JAUGEO_SAUVEGARDE_DOSSIER` (par défaut `./sauvegardes`). Une sauvegarde est faite chaque jour à `JAUGEO_SAUVEGARDE_HEURE` (heure de Dakar) et conservée `JAUGEO_SAUVEGARDE_JOURS` jours. Copiez ce dossier hors du serveur (stockage objet, autre machine) : une sauvegarde gardée sur le même disque ne protège pas contre la perte du serveur.

Restauration (les données actuelles sont remplacées) :

```sh
docker compose stop api web
docker compose exec -T postgres dropdb -U jaugeo jaugeo
docker compose exec -T postgres createdb -U jaugeo jaugeo
docker compose exec -T postgres pg_restore -U jaugeo -d jaugeo --no-owner < sauvegardes/jaugeo-20270301-020000.dump
docker compose start api web
```

Faites un essai de restauration au moins une fois par mois sur une base à part, par exemple avec `createdb jaugeo_essai`.

## Supervision

- Santé : `https://<domaine>/actuator/health` répond `{"status":"UP"}`. À brancher sur une sonde externe, par exemple UptimeRobot toutes les 5 minutes.
- Journaux : `docker compose logs -f api` (erreurs applicatives), `docker compose logs caddy` (accès et certificat).
- Échec de sauvegarde : `docker compose logs sauvegarde | grep ECHEC`.

## Essai sur un poste

Avec `JAUGEO_DOMAINE=localhost`, Caddy utilise une autorité de certification locale : le navigateur affiche un avertissement de certificat. Si les ports 80 et 443 sont pris, passez par un fichier d'override qui les change, par exemple `8088:80` et `8443:443`.

Pour vérifier l'API de bout en bout sur une base neuve, lancez `.\scripts\verifier-api.ps1 -Base https://<domaine> -AdminMdp <mot de passe initial>`. Le script effectue 29 contrôles.
