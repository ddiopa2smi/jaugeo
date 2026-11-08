-- =====================================================================
-- Jaugeo - V3 : utilisateurs, roles, permissions, perimetres, jetons
-- =====================================================================

CREATE TABLE securite.role (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         varchar(30)  NOT NULL UNIQUE,
    libelle      varchar(100) NOT NULL,
    description  varchar(255)
);

CREATE TABLE securite.role_permission (
    role_id     bigint NOT NULL REFERENCES securite.role (id) ON DELETE CASCADE,
    permission  varchar(50) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE securite.utilisateur (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                       uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    identifiant                varchar(50)  NOT NULL,
    nom_complet                varchar(100) NOT NULL,
    email                      varchar(150),
    telephone                  varchar(30),
    mot_de_passe_hash          varchar(255) NOT NULL,
    role_id                    bigint NOT NULL REFERENCES securite.role (id),
    perimetre                  varchar(10)  NOT NULL CHECK (perimetre IN ('RESEAU', 'STATIONS')),
    actif                      boolean NOT NULL DEFAULT true,
    doit_changer_mot_de_passe  boolean NOT NULL DEFAULT true,
    echecs_connexion           integer NOT NULL DEFAULT 0,
    verrouille_jusqua          timestamptz,
    derniere_connexion         timestamptz,
    version                    integer NOT NULL DEFAULT 0,
    cree_le                    timestamptz NOT NULL DEFAULT now(),
    cree_par                   varchar(100) NOT NULL,
    modifie_le                 timestamptz NOT NULL DEFAULT now(),
    modifie_par                varchar(100) NOT NULL
);

-- Identifiant unique sans tenir compte de la casse
CREATE UNIQUE INDEX uq_utilisateur_identifiant ON securite.utilisateur (lower(identifiant));

-- Stations accessibles a un utilisateur dont le perimetre est STATIONS
CREATE TABLE securite.utilisateur_station (
    utilisateur_id  bigint NOT NULL REFERENCES securite.utilisateur (id) ON DELETE CASCADE,
    station_id      bigint NOT NULL REFERENCES referentiel.station (id),
    PRIMARY KEY (utilisateur_id, station_id)
);

-- Jetons de rafraichissement : seule l'empreinte SHA-256 est stockee
CREATE TABLE securite.jeton_rafraichissement (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    utilisateur_id  bigint NOT NULL REFERENCES securite.utilisateur (id) ON DELETE CASCADE,
    empreinte       varchar(64) NOT NULL UNIQUE,
    cree_le         timestamptz NOT NULL DEFAULT now(),
    expire_le       timestamptz NOT NULL,
    revoque_le      timestamptz
);

CREATE INDEX ix_jeton_utilisateur ON securite.jeton_rafraichissement (utilisateur_id);

-- Journal des connexions (reussies et echouees)
CREATE TABLE securite.journal_connexion (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    horodatage   timestamptz NOT NULL DEFAULT now(),
    identifiant  varchar(50) NOT NULL,
    succes       boolean NOT NULL,
    motif        varchar(50),
    adresse_ip   varchar(45)
);

CREATE INDEX ix_journal_connexion_date ON securite.journal_connexion (horodatage DESC);

SELECT audit.activer_audit('securite.role');
SELECT audit.activer_audit('securite.role_permission');
SELECT audit.activer_audit('securite.utilisateur');
SELECT audit.activer_audit('securite.utilisateur_station');
